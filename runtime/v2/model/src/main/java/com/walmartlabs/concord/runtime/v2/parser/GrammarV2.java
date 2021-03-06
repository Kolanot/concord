package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueTypeException;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.runtime.v2.parser.ExitGrammar.exit;
import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.exprFull;
import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.exprShort;
import static com.walmartlabs.concord.runtime.v2.parser.FlowCallGrammar.callFull;
import static com.walmartlabs.concord.runtime.v2.parser.FormsGrammar.callForm;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GroupGrammar.group;
import static com.walmartlabs.concord.runtime.v2.parser.ParallelGrammar.parallelBlock;
import static com.walmartlabs.concord.runtime.v2.parser.SnapshotGrammar.snapshot;
import static com.walmartlabs.concord.runtime.v2.parser.TaskGrammar.taskFull;
import static com.walmartlabs.concord.runtime.v2.parser.TaskGrammar.taskShort;
import static io.takari.parc.Combinators.*;

public final class GrammarV2 {

    public static final Parser.Ref<Atom, YamlValue> value = Parser.ref();
    public static final Parser.Ref<Atom, YamlList> arrayOfValues = Parser.ref();
    public static final Parser.Ref<Atom, YamlObject> object = Parser.ref();

    public static Parser<Atom, Serializable> anyVal = value.map(YamlValue::getValue);
    public static Parser<Atom, String> stringVal = value.map(v -> v.getValue(YamlValueType.STRING));
    public static Parser<Atom, Integer> intVal = value.map(v -> v.getValue(YamlValueType.INT));
    public static Parser<Atom, Boolean> booleanVal = value.map(v -> v.getValue(YamlValueType.BOOLEAN));
    public static Parser<Atom, Map<String, Serializable>> mapVal = value.map(v -> v.getValue(YamlValueType.OBJECT));
    public static Parser<Atom, List<Serializable>> arrayVal = value.map(v -> v.getValue(YamlValueType.ARRAY));
    public static Parser<Atom, Serializable> nonNullVal = value.map(v -> {
        assertNotNull(v);
        return v.getValue();
    });
    public static Parser<Atom, Integer> maybeInt = _val(JsonToken.VALUE_NUMBER_INT).map(v -> v.getValue(YamlValueType.INT));

    public static final Parser.Ref<Atom, List<Step>> stepsVal = Parser.ref();

    private static YamlValueType toType(JsonToken t) {
        switch (t) {
            case VALUE_STRING:
                return YamlValueType.STRING;
            case VALUE_NUMBER_INT:
                return YamlValueType.INT;
            case VALUE_NUMBER_FLOAT:
                return YamlValueType.FLOAT;
            case VALUE_FALSE:
            case VALUE_TRUE:
                return YamlValueType.BOOLEAN;
            case VALUE_NULL:
                return YamlValueType.NULL;
            default:
                throw new IllegalArgumentException("Unknown type: " + t);
        }
    }

    // value := VALUE_STRING | VALUE_NUMBER_INT | VALUE_NUMBER_FLOAT | VALUE_TRUE | VALUE_FALSE | VALUE_NULL | arrayOfValues | object
    @SuppressWarnings("unchecked")
    private static Parser<Atom, YamlValue> _val(JsonToken t) {
        return satisfyToken(t).map(a -> new YamlValue(a.value, toType(t), a.location));
    }

    // arrayOfValues := START_ARRAY value* END_ARRAY
    static {
        arrayOfValues.set(label("Array of values",
                testToken(JsonToken.START_ARRAY).bind(t ->
                        betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                                many(value).map(a -> new YamlList(a.toList(), t.location)))
                )));
    }

    // object := START_OBJECT (FIELD_NAME value)* END_OBJECT
    static {
        object.set(label("Object",
                testToken(JsonToken.START_OBJECT).bind(t ->
                        betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                                many(satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                                        value.map(v -> new KV<>(a.name, v)))))
                                .map(a -> new YamlObject(toMap(a), t.location)))));
    }

    static {
        value.set(choice(choice(
                _val(JsonToken.VALUE_STRING),
                _val(JsonToken.VALUE_NUMBER_INT),
                _val(JsonToken.VALUE_NUMBER_FLOAT),
                _val(JsonToken.VALUE_TRUE),
                _val(JsonToken.VALUE_FALSE),
                _val(JsonToken.VALUE_NULL)),
                arrayOfValues,
                object
        ));
    }

    private static final Parser<Atom, Step> stepObject = label("Process definition step (complex)",
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    choice(choice(parallelBlock, group, exprFull), choice(taskFull, callFull, callForm), snapshot, taskShort)));

    // step := exit | exprShort | parallelBlock | stepObject
    private static final Parser<Atom, Step> step = orError(choice(exit, exprShort, stepObject), YamlValueType.STEP);

    // steps := START_ARRAY step+ END_ARRAY
    static {
        stepsVal.set(
                orError(betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY, many1(step).map(Seq::toList)),
                        YamlValueType.ARRAY_OF_STEP));
    }

    public static Parser<Atom, Step> getProcessStep() {
        return step;
    }

    public static Map<String, YamlValue> toMap(Seq<KV<String, YamlValue>> values) {
        if (values == null) {
            return Collections.emptyMap();
        }

        Map<String, YamlValue> m = new LinkedHashMap<>();
        values.stream().forEach(kv -> m.put(kv.getKey(), kv.getValue()));
        return m;
    }

    private static void assertNotNull(YamlValue v) {
        if (v.getType() != YamlValueType.NULL) {
            return;
        }

        throw new InvalidValueTypeException.Builder()
                .location(v.getLocation())
                .expected(YamlValueType.NON_NULL)
                .actual(v.getType())
                .build();
    }

    private GrammarV2() {
    }
}
