package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.triggers.TriggersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.repository.CachedRepositoryManager.RepositoryCacheDao;
import static com.walmartlabs.concord.server.repository.RepositoryManager.DEFAULT_BRANCH;

@Named
public class GithubEventResourceImpl extends AbstractEventResource implements GithubEventResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResourceImpl.class);

    private static final String EVENT_SOURCE = "github";

    private static final String REPO_ID_KEY = "repositoryId";
    private static final String REPO_NAME_KEY = "repository";
    private static final String PROJECT_NAME_KEY = "project";
    private static final String REPO_BRANCH_KEY = "branch";
    private static final String COMMIT_ID_KEY = "commitId";
    private static final String PUSHER_KEY = "author";

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final RepositoryCacheDao repositoryCacheDao;

    @Inject
    public GithubEventResourceImpl(ProjectDao projectDao,
                                   TriggersDao triggersDao,
                                   RepositoryDao repositoryDao,
                                   RepositoryCacheDao repositoryCacheDao,
                                   PayloadManager payloadManager,
                                   ProcessManager processManager) {

        super(payloadManager, processManager, triggersDao, projectDao);

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.repositoryCacheDao = repositoryCacheDao;
    }

    @Override
    public String push(UUID projectId, UUID repoId, Map<String, Object> event) {
        if (event == null) {
            return "ok";
        }

        String eventBranch = getBranch(event);
        RepositoryEntry repo = repositoryDao.get(projectId, repoId);
        if (repo == null) {
            log.warn("push ['{}', '{}', '{}'] -> repo not found", projectId, repoId, eventBranch);
            return "ok";
        }

        ProjectEntry project = projectDao.get(projectId);

        String repoBranch = Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH);
        if (!repoBranch.equals(eventBranch)) {
            log.info("push ['{}', '{}', '{}'] -> ignore, expected branch '{}'", project, repoId, eventBranch, repoBranch);
            return "ok";
        }

        repositoryCacheDao.updateLastPushDate(repoId, new Date());

        Map<String, Object> triggerConditions = buildConditions(repo, event);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, repo, project, triggerConditions);

        String eventId = repoId.toString();
        int count = process(eventId, EVENT_SOURCE, triggerConditions, triggerEvent);

        log.info("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, triggerConditions, triggerEvent, count);

        return "ok";
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         RepositoryEntry repo,
                                                         ProjectEntry project,
                                                         Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put(COMMIT_ID_KEY, event.get("after"));
        result.put(REPO_ID_KEY, repo.getId());
        result.put(PROJECT_NAME_KEY, project.getName());
        result.putAll(conditions);
        return result;
    }

    private static String getBranch(Map<String, Object> event) {
        String ref = (String) event.get("ref");
        if (ref == null) {
            return null;
        }

        String[] refPath = ref.split("/");
        return refPath[refPath.length - 1];
    }

    private static Map<String, Object> buildConditions(RepositoryEntry repo, Map<String, Object> event) {
        Map<String, Object> result = new HashMap<>();
        result.put(REPO_NAME_KEY, repo.getName());
        result.put(REPO_BRANCH_KEY, Optional.ofNullable(repo.getBranch()).orElse(DEFAULT_BRANCH));
        result.put(PUSHER_KEY, getPusher(event));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getPusher(Map<String, Object> event) {
        Map<String, Object> pusher = (Map<String, Object>) event.get("pusher");
        if (pusher == null) {
            return null;
        }

        return (String) pusher.get("name");
    }

}