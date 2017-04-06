/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.modules.impl.factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.google.common.base.CharMatcher;
import org.auraframework.adapter.ConfigAdapter;
import org.auraframework.annotations.Annotations.ServiceComponent;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.module.ModuleDef;
import org.auraframework.impl.DefinitionAccessImpl;
import org.auraframework.impl.root.component.ModuleDefImpl;
import org.auraframework.modules.ModulesCompiler;
import org.auraframework.modules.ModulesCompilerData;
import org.auraframework.modules.impl.ModulesCompilerJ2V8;
import org.auraframework.system.AuraContext;
import org.auraframework.system.BundleSource;
import org.auraframework.system.DefinitionFactory;
import org.auraframework.system.Location;
import org.auraframework.system.Source;
import org.auraframework.system.TextSource;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.text.Hash;

/**
 * Provides ModuleDef implementation
 */
@ServiceComponent
public class BundleModuleDefFactory implements DefinitionFactory<BundleSource<ModuleDef>, ModuleDef> {

    private ConfigAdapter configAdapter;

    @Override
    public Class<?> getSourceInterface() {
        return BundleSource.class;
    }

    @Override
    public Class<ModuleDef> getDefinitionClass() {
        return ModuleDef.class;
    }

    @Override
    public String getMimeType() {
        return "";
    }

    @Override
    public ModuleDef getDefinition(@CheckForNull DefDescriptor<ModuleDef> descriptor,
                                   @Nonnull BundleSource<ModuleDef> source) throws QuickFixException {
        Map<DefDescriptor<?>, Source<?>> sourceMap = source.getBundledParts();

        // get base source.
        Source<?> baseClassSource = sourceMap.get(descriptor);

        if (baseClassSource == null) {
            // javascript file of the same name as module is required
            throw new InvalidDefinitionException("No base file for " + descriptor,
                    new Location(descriptor.getNamespace() + "/" + descriptor.getName(), -1));
        }

        // compute base file path. baseClassSource must be FileSource to return path for systemId
        String baseFilePath = baseClassSource.getSystemId();
        int start = baseFilePath.lastIndexOf('/', baseFilePath.lastIndexOf('/', baseFilePath.lastIndexOf('/') - 1) - 1);
        String componentPath = baseFilePath.substring(start + 1);

        Location location = new Location(baseClassSource);

        String[] paths = componentPath.split("/");
        if (paths.length > 2) {
            String namespace = paths[0];
            String name = paths[1];

            // ensure module folders adhere to web component (custom element) naming conventions.

            if (CharMatcher.JAVA_UPPER_CASE.matchesAnyOf(namespace)) {
                throw new InvalidDefinitionException("Use lowercase for module folder names. Not " + namespace, location);
            }

            if (namespace.contains("-")) {
                throw new InvalidDefinitionException("Namespace cannot have a hyphen. Not " + namespace, location);
            }

            if (CharMatcher.JAVA_UPPER_CASE.matchesAnyOf(name)) {
                throw new InvalidDefinitionException("Use lowercase and hyphens for module file names. Not " + name, location);
            }
        }

        Map<String, String> sources = new HashMap<>();

        // loop and get contents of all files in the bundle
        sourceMap.forEach( (desc, entrySource) -> {
            String path = entrySource.getSystemId();
            String relativePath = path.substring(start + 1);
            if (entrySource instanceof TextSource) {
                sources.put(relativePath, ((TextSource<?>) entrySource).getContents());
            }
        });

        ModuleDefImpl.Builder builder = new ModuleDefImpl.Builder();

        builder.setDescriptor(descriptor);
        builder.setTagName(descriptor.getDescriptorName());
        builder.setLocation(location);

        // access
        boolean isInInternalNamespace = configAdapter.isInternalNamespace(descriptor.getNamespace());
        builder.setAccess(new DefinitionAccessImpl(
                isInInternalNamespace ? AuraContext.Access.INTERNAL : AuraContext.Access.PUBLIC));

        // module
        builder.setPath(baseFilePath);
        builder.setCustomElementName(getCustomElementName(componentPath));

        ModulesCompiler compiler = new ModulesCompilerJ2V8();
        ModulesCompilerData compilerData;
        try {
            compilerData = compiler.compile(componentPath, sources);
        } catch (Exception e) {
            throw new InvalidDefinitionException(descriptor + ": " + e.getMessage(), location, e);
        }
        String compiledCode = processCompiledCode(descriptor, compilerData.code, location);
        builder.setCompiledCode(compiledCode);
        builder.setModuleDependencies(compilerData.bundleDependencies);
        builder.setOwnHash(calculateOwnHash(descriptor + compiledCode));
        return builder.build();

    }

    /**
     * Removes amd define function and replaces with specific Aura handling function
     * and wraps in function
     *
     * @param descriptor module descriptor
     * @param code compiled code
     * @return code results
     * @throws InvalidDefinitionException if code from compiler does not start with define for amd
     */
    private String processCompiledCode(DefDescriptor<ModuleDef> descriptor, String code, Location location) throws InvalidDefinitionException {
        if (!code.substring(0, 7).equals("define(")) {
            throw new InvalidDefinitionException("Compiled code does not start with AMD 'define'", location);
        }
        StringBuilder processedCode = new StringBuilder();
        processedCode
                .append("function() { $A.componentService.addModule('")
                .append(descriptor.getQualifiedName()).append("', ")
                .append(code.substring(7, code.length()))
                .append("}");
        return processedCode.toString();
    }

    private String getCustomElementName(String path) {
        String[] paths = path.split(File.separator);
        int length = paths.length;
        if (length > 2) {
            String name = paths[1];
            String namespace = paths[0];
            return namespace + "-" + name;
        }
        return "";
    }

    private String calculateOwnHash(String input) {
        Hash.StringBuilder hashBuilder = new Hash.StringBuilder();
        hashBuilder.addString(input);
        return hashBuilder.build().toString();
    }

    @Inject
    public void setConfigAdapter(ConfigAdapter configAdapter) {
        this.configAdapter = configAdapter;
    }
}
