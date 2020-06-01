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

  def "exec Julia code without open"() {
    setup:
      def julia = new JuliaBridge()
    when:
      def rsp = julia.exec('println(1+2)')
      julia.close()
    then:
      rsp == ['3']
  }

  def "exec Julia code with open"() {
    setup:
      def julia = new JuliaBridge()
    when:
      julia.open()
      def rsp1 = julia.exec('println(1+2)')
      def rsp2 = julia.exec('1+2')
      julia.close()
    then:
      rsp1 == ['3']
      rsp2 == []
  }

  def "get Julia variables"() {
    setup:
      def julia = new JuliaBridge()
    when:
      julia.open()
      julia.exec(cmd)
      def rsp = julia.get('x')
      julia.close()
    then:
      rsp.class == t
      rsp == v
    where:
      cmd              | t         | v
      'x = Int64(7)'   | Long      | 7
      'x = Int32(7)'   | Integer   | 7
      'x = Int16(7)'   | Short     | 7
      'x = Int8(7)'    | Byte      | 7
      'x = Float64(7)' | Double    | 7.0
      'x = Float32(7)' | Float     | 7.0
      'x = Inf64'      | Double    | Double.POSITIVE_INFINITY
      'x = -Inf32'     | Float     | Float.NEGATIVE_INFINITY
      'x = NaN64'      | Double    | Double.NaN
      'x = "7"'        | String    | "7"
      'x = "αβγ"'      | String    | "αβγ"
  }

  def "get Julia null variable"() {
    setup:
      def julia = new JuliaBridge()
    when:
      julia.open()
      julia.exec('x = nothing')
      def rsp1 = julia.get('x')
      julia.exec('x = missing')
      def rsp2 = julia.get('x')
      julia.close()
    then:
      rsp1 == null
      rsp2 == null
  }

}
