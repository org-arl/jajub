package org.arl.jajub

import spock.lang.Specification

class JuliaBridgeTest extends Specification {

  def "get Julia version"() {
    setup:
      def julia = new JuliaBridge()
    when:
      julia.open()
      def v = julia.juliaVersion
      julia.close()
    then:
      v.startsWith('Julia Version ')
  }

}
