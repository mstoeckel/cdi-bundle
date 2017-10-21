package com.cognodyne.dw.cdi;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableSet;

@JsonDeserialize(builder = CdiConfiguration.Builder.class)
public class CdiConfiguration {
    private final Set<Pattern> includes;
    private final Set<Pattern> excludes;

    private CdiConfiguration(Set<Pattern> includes, Set<Pattern> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<Pattern> getIncludes() {
        return includes;
    }

    public Set<Pattern> getExcludes() {
        return excludes;
    }

    boolean include(Class<?> cls) {
        String name = cls.getName();
        if (this.excludes.stream().anyMatch(p -> p.matcher(name).matches())) {
            return this.includes.stream().anyMatch(p -> p.matcher(name).matches());
        }
        return true;
    }

    public static final class Builder {
        @JsonProperty
        private List<String> includes = Collections.emptyList();
        @JsonProperty
        private List<String> excludes = Collections.emptyList();

        private Builder() {
        }

        public CdiConfiguration build() {
            ImmutableSet.Builder<Pattern> includesBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<Pattern> excludesBuilder = ImmutableSet.builder();
            this.includes.stream().forEach(str -> includesBuilder.add(Pattern.compile(str)));
            this.excludes.stream().forEach(str -> excludesBuilder.add(Pattern.compile(str)));
            return new CdiConfiguration(includesBuilder.build(), excludesBuilder.build());
        }

        public Builder includes(List<String> includes) {
            if (includes != null) {
                this.includes = includes;
            }
            return this;
        }

        public Builder excludes(List<String> excludes) {
            if (excludes != null) {
                this.excludes = excludes;
            }
            return this;
        }
    }
}
