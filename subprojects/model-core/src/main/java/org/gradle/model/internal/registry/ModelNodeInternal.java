/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.registry;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

abstract class ModelNodeInternal implements MutableModelNode {

    private static final Supplier<List<MutatorRuleBinder<?>>> LIST_SUPPLIER = new Supplier<List<MutatorRuleBinder<?>>>() {
        @Override
        public List<MutatorRuleBinder<?>> get() {
            return Lists.newArrayList();
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelNodeInternal.class);

    private CreatorRuleBinder creatorBinder;
    private final Set<ModelNodeInternal> dependencies = Sets.newHashSet();
    private final Set<ModelNodeInternal> dependents = Sets.newHashSet();
    private ModelNode.State state = ModelNode.State.Known;
    private boolean hidden;
    private final List<ModelRuleDescriptor> executedRules = Lists.newArrayList();

    public ModelNodeInternal(CreatorRuleBinder creatorBinder) {
        this.creatorBinder = creatorBinder;
    }

    public CreatorRuleBinder getCreatorBinder() {
        return creatorBinder;
    }

    public void replaceCreatorRuleBinder(CreatorRuleBinder newCreatorBinder) {
        if (getState() != State.Known) {
            throw new IllegalStateException("Cannot replace creator rule binder when not in known state (node: " + this + ", state: " + getState() + ")");
        }

        ModelCreator newCreator = newCreatorBinder.getCreator();
        ModelCreator oldCreator = creatorBinder.getCreator();

        // Can't change type
        if (!oldCreator.getPromise().equals(newCreator.getPromise())) {
            throw new IllegalStateException("can not replace node " + getPath() + " with different promise (old: " + oldCreator.getPromise() + ", new: " + newCreator.getPromise() + ")");
        }

        // Can't have different inputs
        if (!newCreator.getInputs().equals(oldCreator.getInputs())) {
            Joiner joiner = Joiner.on(", ");
            throw new IllegalStateException("can not replace node " + getPath() + " with creator with different input bindings (old: [" + joiner.join(oldCreator.getInputs()) + "], new: [" + joiner.join(newCreator.getInputs()) + "])");
        }

        this.creatorBinder = newCreatorBinder;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public boolean isEphemeral() {
        return creatorBinder.getCreator().isEphemeral();
    }

    private static ListMultimap<ModelNode.State, MutatorRuleBinder<?>> createMutatorsMap() {
        return Multimaps.newListMultimap(new EnumMap<ModelNode.State, Collection<MutatorRuleBinder<?>>>(ModelNode.State.class), LIST_SUPPLIER);
    }

    public void notifyFired(RuleBinder binder) {
        assert binder.isBound() : "RuleBinder must be in a bound state";
        for (ModelBinding inputBinding : binder.getInputBindings()) {
            ModelNodeInternal node = inputBinding.getNode();
            dependencies.add(node);
            node.dependents.add(this);
        }
        executedRules.add(binder.getDescriptor());
    }

    public Iterable<? extends ModelNode> getDependencies() {
        return dependencies;
    }

    public Iterable<? extends ModelNode> getDependents() {
        return dependents;
    }

    public ModelPath getPath() {
        return creatorBinder.getCreator().getPath();
    }

    public ModelRuleDescriptor getDescriptor() {
        return creatorBinder.getDescriptor();
    }

    public ModelNode.State getState() {
        return state;
    }

    public void setState(ModelNode.State state) {
        this.state = state;
    }

    public boolean isMutable() {
        return state.mutable;
    }

    public ModelPromise getPromise() {
        return creatorBinder.getCreator().getPromise();
    }

    public ModelAdapter getAdapter() {
        return creatorBinder.getCreator().getAdapter();
    }

    @Override
    public String toString() {
        return getPath().toString();
    }

    public abstract Iterable<? extends ModelNodeInternal> getLinks();

    public void reset() {
        if (getState() != State.Known) {
            setState(State.Known);
            setPrivateData(ModelType.untyped(), null);

            for (ModelNodeInternal dependent : dependents) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("resetting dependent node of {}: {}", this, dependent);
                }
                dependent.reset();
            }

            for (ModelNodeInternal child : getLinks()) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("resetting child node of {}: {}", this, child);
                }

                child.reset();
            }
        }
    }

    @Override
    public Optional<String> getValueDescription() {
        this.ensureUsable();
        return this.getAdapter().getValueDescription(this);
    }

    @Override
    public Optional<String> getTypeDescription() {
        this.ensureUsable();
        ModelView<?> modelView = getAdapter().asReadOnly(ModelType.untyped(), this, null);
        if (modelView != null) {
            ModelType<?> type = modelView.getType();
            if (type != null) {
                return Optional.of(type.toString());
            }
            modelView.close();
        }
        return Optional.absent();
    }

    @Override
    public List<ModelRuleDescriptor> getExecutedRules() {
        return this.executedRules;
    }

    @Override
    public boolean contentEquals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelNodeInternal)) {
            return false;
        }

        ModelNodeInternal that = (ModelNodeInternal) other;

        if (!Objects.equal(this.getPrivateData(), that.getPrivateData())) {
            return false;
        }

        Iterator<? extends ModelNodeInternal> thisLinks = this.getLinks().iterator();
        Iterator<? extends ModelNodeInternal> thatLinks = that.getLinks().iterator();
        while (thisLinks.hasNext()) {
            if (!thatLinks.hasNext()) {
                return false;
            }
            ModelNodeInternal thisLink = thisLinks.next();
            ModelNodeInternal thatLink = thatLinks.next();
            if (!thisLink.contentEquals(thatLink)) {
                return false;
            }
        }
        return !thatLinks.hasNext();
    }

    @Override
    public int contentHashCode() {
        Object privateData = getPrivateData();
        int hashCode = privateData != null ? privateData.hashCode() : 0;
        for (ModelNodeInternal link : getLinks()) {
            hashCode = 31 * hashCode + link.contentHashCode();
        }
        return hashCode;
    }
}
