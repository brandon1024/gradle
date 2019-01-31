/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.component.model

import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.AmbiguousConfigurationSelectionException
import org.gradle.internal.component.NoMatchingConfigurationSelectionException
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.AttributeTestUtil.attributes

class AttributeConfigurationSelectorTest extends Specification {
    private final AttributesSchemaInternal attributesSchema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.valueSnapshotter())

    private ComponentResolveMetadata targetComponent
    private ConfigurationMetadata selected
    private ImmutableAttributes consumerAttributes = ImmutableAttributes.EMPTY
    private List<Capability> requestedCapabilities = []

    @Unroll
    def "selects a variant when there's no ambiguity"() {
        given:
        component(
                variant("api", attributes('org.gradle.usage': 'java-api')),
                variant("runtime", attributes('org.gradle.usage': 'java-runtime'))
        )

        and:
        consumerAttributes('org.gradle.usage': usage)

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        usage          | expected
        'java-api'     | 'api'
        'java-runtime' | 'runtime'
    }

    def "fails to select a variant when there are more than one candidate"() {
        given:
        component(
                variant("api1", attributes('org.gradle.usage': 'java-api')),
                variant("api2", attributes('org.gradle.usage': 'java-api'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        when:
        performSelection()

        then:
        AmbiguousConfigurationSelectionException e = thrown()
        e.message == '''Cannot choose between the following variants of org:lib:1.0:
  - api1
  - api2
All of them match the consumer attributes:
  - Variant 'api1' capability org:lib:1.0:
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.
  - Variant 'api2' capability org:lib:1.0:
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.'''
    }

    def "fails to select a variant when there no matching candidates"() {
        given:
        component(
                variant("api", attributes('org.gradle.usage': 'java-api')),
                variant("runtime", attributes('org.gradle.usage': 'java-runtime'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'cplusplus-headers')

        when:
        performSelection()

        then:
        NoMatchingConfigurationSelectionException e = thrown()
        e.message == '''Unable to find a matching variant of org:lib:1.0:
  - Variant 'api' capability org:lib:1.0:
      - Required org.gradle.usage 'cplusplus-headers' and found incompatible value 'java-api'.
  - Variant 'runtime' capability org:lib:1.0:
      - Required org.gradle.usage 'cplusplus-headers' and found incompatible value 'java-runtime'.'''
    }

    @Unroll
    def "can select a variant thanks to the capabilities"() {
        given:
        component(
                variant("api1", attributes('org.gradle.usage': 'java-api'), capability('first')),
                variant("api2", attributes('org.gradle.usage': 'java-api'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')
        requestCapability capability(cap)

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        cap      | expected
        'first'  | 'api1'
        'second' | 'api2'
    }

    @Unroll
    def "can select a variant thanks to the implicit capability"() {
        given:
        component(
                variant("api1", attributes('org.gradle.usage': 'java-api')),
                variant("api2", attributes('org.gradle.usage': 'java-api'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        if (cap) {
            requestCapability capability(cap)
        }

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        cap      | expected
        null     | 'api1'
        'lib'    | 'api1'
        'second' | 'api2'
    }


    def "fails if more than one variant provides the implicit capability"() {
        given:
        component(
                variant("api1", attributes('org.gradle.usage': 'java-api')),
                variant("api2", attributes('org.gradle.usage': 'java-api'), capability('lib'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        requestCapability capability('lib')

        when:
        performSelection()

        then:
        AmbiguousConfigurationSelectionException e = thrown()
        e.message == '''Cannot choose between the following variants of org:lib:1.0:
  - api1
  - api2
All of them match the consumer attributes:
  - Variant 'api1' capability org:lib:1.0:
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.
  - Variant 'api2' capabilities org:lib:1.0 and org:second:1.0:
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.'''
    }

    private void performSelection() {
        selected = AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(
                consumerAttributes,
                requestedCapabilities,
                targetComponent,
                attributesSchema
        )
    }

    private consumerAttributes(Map<String, Object> attrs) {
        this.consumerAttributes = attributes(attrs)
    }

    private void requestCapability(Capability c) {
        requestedCapabilities << c
    }

    private void component(ConfigurationMetadata... variants) {
        targetComponent = Stub(ComponentResolveMetadata) {
            getModuleVersionId() >> Stub(ModuleVersionIdentifier) {
                getGroup() >> 'org'
                getName() >> 'lib'
                getVersion() >> '1.0'
            }
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> 'org:lib:1.0'
            }
            getVariantsForGraphTraversal() >> Optional.of(
                    ImmutableList.copyOf(variants)
            )
            getAttributesSchema() >> attributesSchema
        }
    }

    private ConfigurationMetadata variant(String name, ImmutableAttributes attributes, Capability... capabilities) {
        Stub(ConfigurationMetadata) {
            getName() >> name
            getAttributes() >> attributes
            getCapabilities() >> Mock(CapabilitiesMetadata) {
                getCapabilities() >> ImmutableList.copyOf(capabilities)
            }
        }
    }

    private Capability capability(String group, String name, String version = '1.0') {
        Stub(Capability) {
            getGroup() >> group
            getName() >> name
            getVersion() >> version
        }
    }

    private Capability capability(String name) {
        capability('org', name)
    }
}
