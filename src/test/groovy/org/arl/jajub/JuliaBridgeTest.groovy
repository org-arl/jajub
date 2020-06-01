package org.arl.jajub

import spock.lang.Specification
import spock.lang.Shared

class JuliaBridgeTest extends Specification {

  @Shared julia = new JuliaBridge()

  def setupSpec() {
    julia.open()
  }

  def cleanupSpec() {
    julia.close()
  }

  def "get Julia version"() {
    when:
      def v = julia.juliaVersion
    then:
      v.startsWith('Julia Version ')
  }

  def "exec Julia code"() {
    setup:
      def julia = new JuliaBridge()
    when:
      def rsp1 = julia.exec('println(1+2)')
      def rsp2 = julia.exec('1+2')
    then:
      rsp1 == ['3']
      rsp2 == []
  }

  def "get Julia variables"() {
    when:
      julia.exec(cmd)
      def rsp = julia.get('x')
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
    when:
      julia.exec('x = nothing')
      def rsp1 = julia.get('x')
      julia.exec('x = missing')
      def rsp2 = julia.get('x')
    then:
      rsp1 == null
      rsp2 == null
  }

  def "get Julia variables"() {
    when:
      julia.exec(cmd)
      def rsp = julia.get('x')
    then:
      rsp.class == t
      rsp.data == v
      rsp.dims == d
    where:
      cmd                          | t            | d       | v
      'x = Int64[-2,3]'            | LongArray    | [2]     |[-2,3] as long[]
      'x = Int32[-2,3]'            | IntegerArray | [2]     |[-2,3] as int[]
      'x = Int16[-2,3]'            | ShortArray   | [2]     |[-2,3] as short[]
      'x = Int8[-2,3]'             | ByteArray    | [2]     |[-2,3] as byte[]
      'x = Float64[-2,3,NaN,Inf]'  | DoubleArray  | [4]     |[-2,3,Double.NaN,Double.POSITIVE_INFINITY] as double[]
      'x = Float32[-2,3,NaN,-Inf]' | FloatArray   | [4]     |[-2,3,Float.NaN,Float.NEGATIVE_INFINITY] as float[]
      'x = 1:10'                   | LongArray    | [10]    |[1,2,3,4,5,6,7,8,9,10] as long[]
      'x = Int64[1 2; 3 4]'        | LongArray    | [2,2]   |[1,3,2,4] as long[]
      'x = Int32[1 2; 3 4]'        | IntegerArray | [2,2]   |[1,3,2,4] as int[]
      'x = Int16[1 2; 3 4]'        | ShortArray   | [2,2]   |[1,3,2,4] as short[]
      'x = Int8[1 2; 3 4]'         | ByteArray    | [2,2]   |[1,3,2,4] as byte[]
      'x = Float64[1 2; 3 4]'      | DoubleArray  | [2,2]   |[1,3,2,4] as double[]
      'x = Float32[1 2; 3 4]'      | FloatArray   | [2,2]   |[1,3,2,4] as float[]
  }

}
