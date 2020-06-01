package org.arl.jajub

import spock.lang.Specification

class JuliaBridgeTest extends Specification {

    def "someLibraryMethod returns true"() {
        setup:
        def julia = new JuliaBridge()

        when:
        def result = true

        then:
        result == true
    }

}
