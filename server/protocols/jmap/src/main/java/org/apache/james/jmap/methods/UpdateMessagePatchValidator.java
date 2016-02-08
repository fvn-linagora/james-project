/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.methods;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.UpdateMessagePatch;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class UpdateMessagePatchValidator implements Validator<ObjectNode> {

    private final ObjectMapper parser;

    @Inject
    @VisibleForTesting UpdateMessagePatchValidator(ObjectMapper parser) {
        this.parser = parser;
    }

    @Override
    public boolean isValid(ObjectNode patch) {
        return validate(patch).isEmpty();
    }

    @Override
    public Set<ValidationResult> validate(ObjectNode json) {
        ImmutableSet.Builder<ValidationResult> compilation = ImmutableSet.builder();
        try {
            parser.readValue(json.toString(), UpdateMessagePatch.class);
        } catch (InvalidFormatException e) {
            compilation.add(ValidationResult.builder()
                    .property(firstFieldFrom(e.getPath()))
                    .message(e.getMessage())
                    .build());
        } catch (JsonMappingException e) {
            e.getPath().stream().forEach(ref ->
                    compilation.add(ValidationResult.builder()
                        .property(firstFieldFrom(e.getPath()))
                        // .property(ref.getFieldName())
                        .message(e.getMessage())
                        .build()));
        } catch (IOException e) {
            compilation.add(ValidationResult.builder()
                    .message(e.getMessage())
                    .build());
        }
        return compilation.build();
    }

    private String firstFieldFrom(List<JsonMappingException.Reference> references) {
        return references.stream()
                .map(JsonMappingException.Reference::getFieldName)
                .map(MessageProperties.MessageProperty::valueOf)
                .findFirst()
                .get()
                .asFieldName();
    }
}
