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
package org.auraframework.impl.root;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.auraframework.Aura;
import org.auraframework.builder.PlatformDefBuilder;
import org.auraframework.def.AttributeDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.MethodDef;
import org.auraframework.def.PlatformDef;
import org.auraframework.throwable.quickfix.QuickFixException;

public abstract class PlatformDefImpl<T extends PlatformDef> extends BundleDefImpl<T> implements PlatformDef {

    private static final long serialVersionUID = -5903259567383044872L;
    protected final Map<DefDescriptor<AttributeDef>, AttributeDef> attributeDefs;
    protected final Double minVersion;
    protected Set<String> targets;
    private final SupportLevel support;

    protected PlatformDefImpl(Builder<T> builder) {
        super(builder);
        this.support = builder.support;
        
        if (builder.attributeDefs == null || builder.attributeDefs.size() == 0) {
            this.attributeDefs = ImmutableMap.of();
        } else {
            this.attributeDefs = Collections.unmodifiableMap(builder.attributeDefs);
        }
        
        this.minVersion = builder.minVersion;
        this.targets = Collections.unmodifiableSet(builder.targets);
    }

    @Override
    public abstract Map<DefDescriptor<AttributeDef>, AttributeDef> getAttributeDefs() throws QuickFixException;

    @Override
    public AttributeDef getAttributeDef(String name) throws QuickFixException {
        return getAttributeDefs().get(Aura.getDefinitionService().getDefDescriptor(name, AttributeDef.class));
    }
    
    /**
     * Define the minimum API version that a component should be at to use the current component.
     * @return Double value if set, null otherwise
     */
    @Override
    public Double getMinVersion() {
        return this.minVersion;
    }

    @Override
    public Set<String> getTargets() {
        return this.targets;
    }

    @Override
    public SupportLevel getSupport() {
        return this.support;
    }

    public abstract static class Builder<T extends PlatformDef> extends BundleDefImpl.Builder<T> implements PlatformDefBuilder<T> {
        private final Map<DefDescriptor<AttributeDef>, AttributeDef> attributeDefs = Maps.newLinkedHashMap();
        private Double minVersion;
        protected Set<String> targets = Collections.emptySet();
        private SupportLevel support = SupportLevel.PROTO;

        public Builder(Class<T> defClass) {
            super(defClass);
        }

        /**
         * Gets the attributeDefs for this instance.
         *
         * @return The attributeDefs.
         */
        public Map<DefDescriptor<AttributeDef>, AttributeDef> getAttributeDefs() {
            return attributeDefs;
        }

        /**
         * Sets the attributeDefs for this instance.
         */
        public Builder<T> addAttributeDef(DefDescriptor<AttributeDef> defDescriptor, AttributeDef attributeDef) {
            attributeDefs.put(defDescriptor, attributeDef);
            return this;
        }

        /**
         * Sets the attributeDefs for this instance.
         * We use a setter instead of allowing modification of the builder properties so we always know that attributeDefs is a collection. 
         * Otherwise it could be set to null.
         */
        public Builder<T> setAttributeDefs(Map<DefDescriptor<AttributeDef>, AttributeDef> attributeDefs) {
        		this.attributeDefs.clear();
        		if(attributeDefs != null) {
        			this.attributeDefs.putAll(attributeDefs);
        		}
            return this;
        }


        public void setTargets(Set<String> targets) {
            this.targets = targets;
        }

        public void addTarget(String target) {
            if (targets.isEmpty()) {
                targets = Sets.newHashSet();
            }
            this.targets.add(target);
        }


        public Builder<T> setMinVersion(Double minVersion) {
            this.minVersion = minVersion;
            return this;
        }
        
        public Double getMinVersion() {
        		return minVersion;
        }

        @Override
        public Builder<T> setSupport(SupportLevel support) {
            if (support != null) {
                this.support = support;
            }
            return this;
        }
    }
}
