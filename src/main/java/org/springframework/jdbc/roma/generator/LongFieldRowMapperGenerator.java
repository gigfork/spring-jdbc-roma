/*
 * Copyright 2002-2013 the original author or authors.
 *
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
 */

package org.springframework.jdbc.roma.generator;

import java.lang.reflect.Field;

import org.springframework.jdbc.roma.config.manager.ConfigManager;

public class LongFieldRowMapperGenerator<T> extends AbstractRowMapperFieldGenerator<T> {

	public LongFieldRowMapperGenerator(Field field, ConfigManager configManager) {
		super(field, configManager);
	}
	
	@Override
	public String doFieldMapping(Field f) {
		String setterMethodName = getSetterMethodName(f);
		String setValueExpr = RESULT_SET_ARGUMENT + ".getLong(\"" + columnName + "\")";
		if (f.getType().equals(Long.class)) {
			setValueExpr = "Long.valueOf" + "(" + setValueExpr + ")";
		}
		return
			wrapWithNullCheck(	
				GENERATED_OBJECT_NAME + "." + setterMethodName + "(" + setValueExpr + ");",
				setterMethodName);
	}

}
