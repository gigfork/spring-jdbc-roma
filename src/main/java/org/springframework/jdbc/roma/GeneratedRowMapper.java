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

package org.springframework.jdbc.roma;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.roma.config.manager.ConfigManager;
import org.springframework.jdbc.roma.domain.model.config.RowMapperClassConfig;
import org.springframework.jdbc.roma.factory.DefaultRowMapperGeneratorFactory;
import org.springframework.jdbc.roma.factory.RowMapperGeneratorFactory;
import org.springframework.jdbc.roma.generator.RowMapperFieldGenerator;
import org.springframework.jdbc.roma.proxy.ProxyHelper;
import org.springframework.jdbc.roma.proxy.ProxyListLoader;
import org.springframework.jdbc.roma.proxy.ProxyObjectLoader;
import org.springframework.jdbc.roma.util.ReflectionUtil;
import org.springframework.jdbc.roma.util.SpringUtil;

/**
 * @author Serkan ÖZAL
 */
public class GeneratedRowMapper<T> extends AbstractRowMapper<T> {
	
	protected static final Class<?>[] DEFAULT_CLASSES_TO_BE_ADDED = {
		RowMapper.class,
		ResultSet.class,
		Blob.class,
		Clob.class,
		GeneratedRowMapper.class,
		ProxyHelper.class,
		ProxyObjectLoader.class,
		ProxyListLoader.class,
		SpringUtil.class
	};
	
	protected static final Logger logger = LoggerFactory.getLogger(GeneratedRowMapper.class);
	
	protected static final Map<String, Class<? extends RowMapper<?>>> createdRowMappers = 
							new HashMap<String,  Class<? extends RowMapper<?>>>();
	
	protected List<RowMapperFieldGenerator<T>> rowMappers = new ArrayList<RowMapperFieldGenerator<T>>();
	protected RowMapperGeneratorFactory<T> rowMapperFactory = new DefaultRowMapperGeneratorFactory<T>(configManager);
	protected Map<RowMapperFieldGenerator<T>, Field> rowMapperFieldMap = new HashMap<RowMapperFieldGenerator<T>, Field>();
	protected ClassPool cp = ClassPool.getDefault();
	protected RowMapper<T> generatedRowMapper;
	protected List<Class<?>> additionalClasses = new ArrayList<Class<?>>();
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GeneratedRowMapper(Class<T> cls) {
		super(cls);
		RowMapperClassConfig rmcc = configManager.getRowMapperClassConfig(cls);
		if (rmcc != null) {
			Class<? extends RowMapperGeneratorFactory> generatorFactoryCls = rmcc.getGeneratorFactoryClass();
			if (generatorFactoryCls != null && generatorFactoryCls.equals(RowMapperGeneratorFactory.class) == false) {
				try {
					rowMapperFactory = generatorFactoryCls.newInstance();
				} 
				catch (Exception e) {
					logger.error("Unable to create instance of " + generatorFactoryCls.getName(), e);
				} 
			}
		}
		init();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public GeneratedRowMapper(Class<T> cls, ConfigManager configManager) {
		super(cls, configManager);
		RowMapperClassConfig rmcc = configManager.getRowMapperClassConfig(cls);
		if (rmcc != null) {
			Class<? extends RowMapperGeneratorFactory> generatorFactoryCls = rmcc.getGeneratorFactoryClass();
			if (generatorFactoryCls != null && generatorFactoryCls.equals(RowMapperGeneratorFactory.class) == false) {
				try {
					rowMapperFactory = generatorFactoryCls.newInstance();
				} 
				catch (Exception e) {
					logger.error("Unable to create instance of " + generatorFactoryCls.getName(), e);
				} 
			}
		}
		init();
	}
	
	public GeneratedRowMapper(Class<T> cls, Class<? extends RowMapperGeneratorFactory<T>> rowMapperFactoryCls) {
		super(cls);
		try {
			this.rowMapperFactory = rowMapperFactoryCls.newInstance();
		} 
		catch (Exception e) {
			logger.error("Unable to create instance of " + rowMapperFactoryCls.getName(), e);
		} 
		init();
	}

	public GeneratedRowMapper(Class<T> cls, Class<? extends RowMapperGeneratorFactory<T>> rowMapperFactoryCls,
			ConfigManager configManager) {
		super(cls, configManager);
		try {
			this.rowMapperFactory = rowMapperFactoryCls.newInstance();
		} 
		catch (Exception e) {
			logger.error("Unable to create instance of " + rowMapperFactoryCls.getName(), e);
		} 
		init();
	}
	
	public GeneratedRowMapper(Class<T> cls, RowMapperGeneratorFactory<T> rowMapperFactory) {
		super(cls);
		if (rowMapperFactory != null) {
			this.rowMapperFactory = rowMapperFactory;
		}	
		init();
	}
	
	public GeneratedRowMapper(Class<T> cls, RowMapperGeneratorFactory<T> rowMapperFactory, 
			ConfigManager configManager) {
		super(cls, configManager);
		if (rowMapperFactory != null) {
			this.rowMapperFactory = rowMapperFactory;
		}	
		init();
	}
	
	protected void init() {
		for (Class<?> cls : DEFAULT_CLASSES_TO_BE_ADDED) {
			addAdditionalClass(cls);
		}
		reset();
		createRowMapperGenerators();
		generateRowMapper();
	}
	
	protected void createRowMapperGenerators() {
		List<Field> fields = ReflectionUtil.getAllFields(cls);
		if (fields != null) {
			for (Field f : fields) {
				RowMapperFieldGenerator<T> rmfg = rowMapperFactory.createRowMapperFieldGenerator(f);
				if (rmfg != null) {
					rowMappers.add(rmfg);
					rmfg.assignedToRowMapper(this);
					rowMapperFieldMap.put(rmfg, f);
				}	
			}
		}
	}
	
	public void addAdditionalClass(Class<?> cls) {
		additionalClasses.add(cls);
		cp.importPackage(cls.getPackage().getName());
		cp.appendClassPath(new ClassClassPath(cls));
	}
	
	public List<Class<?>> getAdditionalClasses() {
		return additionalClasses;
	}

	@SuppressWarnings("unchecked")
	protected void generateRowMapper() {
		try {
			final String generatedClassName = cls.getSimpleName() + "GeneratedRowMapper";
			
			Class<? extends RowMapper<?>> generatedClass = createdRowMappers.get(generatedClassName);
			
			if (generatedClass == null) {
				CtClass generatedRowMapperCls = cp.getOrNull(cls.getSimpleName() + "GeneratedRowMapper");
				if (generatedRowMapperCls == null) {
					generatedRowMapperCls = cp.makeClass(generatedClassName);
					generatedRowMapperCls.defrost();
					generatedRowMapperCls.addInterface(cp.get(RowMapper.class.getName()));
					
					CtMethod mapRowMethod = 
						new CtMethod(
								cp.get(Object.class.getName()), 
								"mapRow",
								new CtClass[] {cp.get(ResultSet.class.getName()), cp.get(int.class.getName())},
								generatedRowMapperCls);
					mapRowMethod.setModifiers(Modifier.PUBLIC);
					
					StringBuffer methodBody = new StringBuffer();
					String typeName = cls.getName();
					methodBody.append("{").append("\n");
					methodBody.
						append("\t").
						append(
							typeName + " " + RowMapperFieldGenerator.GENERATED_OBJECT_NAME + 
							" = " + 
							"new " + typeName + "();").append("\n");
					for (RowMapperFieldGenerator<T> rmfg : rowMappers) {
						Field f = rowMapperFieldMap.get(rmfg);
						methodBody.append("\t").append(rmfg.generateFieldMapping(f)).append("\n");	
					}
					methodBody.
						append("\t").
						append("return" + " " + RowMapperFieldGenerator.GENERATED_OBJECT_NAME + ";").append("\n");
					methodBody.append("}").append("\n");
					
					String methodCode = 
						"try" + "\n" +
						"{" + "\n" +
						"\t" + methodBody.toString() + 
						"}" + "\n" +
						"catch (Throwable t)" + "\n" +
						"{" + "\n" +
						"\t" + "t.printStackTrace();" + "\n" +
						"\t" + "return null;" + "\n" +
						"}";
					
					if (logger.isDebugEnabled()) {
						logger.debug("GeneratedRowMapper: " + generatedClassName + "\n" + methodCode);
					}
					
					mapRowMethod.setBody(methodCode);
					generatedRowMapperCls.addMethod(mapRowMethod);
				}	
				
				generatedClass = generatedRowMapperCls.toClass();
				createdRowMappers.put(generatedClassName, generatedClass);
				generatedRowMapperCls.detach();
			}	

			generatedRowMapper = (RowMapper<T>)generatedClass.newInstance();
		} 
		catch (Throwable e) {
			logger.error("Error occured while generating rowmapper", e);
		} 
	}
	
	protected void reset() {
		try {
			this.obj = cls.newInstance();
		} 
		catch (Exception e) {
			logger.error("Unable to create instance of " + cls.getName(), e);
		} 
	}
	
	@Override
	public T mapRow(ResultSet rs, int rowNum) throws SQLException {
		try {
			return generatedRowMapper.mapRow(rs, rowNum);
		}
		catch (Throwable e) {
			logger.error("Error occured while mapping row", e);
			return null;
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static RowMapper provideRowMapper(String clsName) {
		try {
			return new GeneratedRowMapper(Class.forName(clsName));
		} 
		catch (ClassNotFoundException e) {
			logger.error("Unable to find class: " + clsName, e);
			return null;
		}
	}
	
}
