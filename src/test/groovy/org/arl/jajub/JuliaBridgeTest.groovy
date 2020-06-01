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

  def "get version"() {
    when:
      def v = julia.juliaVersion
    then:
      v.startsWith('Julia Version ')
  }

  def "exec code"() {
    setup:
      def julia = new JuliaBridge()
    when:
      def rsp1 = julia.exec('println(1+2)')
      def rsp2 = julia.exec('1+2')
    then:
      rsp1 == ['3']
      rsp2 == []
  }

  def "get variables"() {
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
      'x = "7"'        | String    | '7'
      'x = "αβγ"'      | String    | 'αβγ'
      'x = "a\nb"'     | String    | 'a\nb'
      'x = """a"b"""'  | String    | 'a"b'
  }

  def "get null variable"() {
    when:
      julia.exec('x = nothing')
      def rsp1 = julia.get('x')
      julia.exec('x = missing')
      def rsp2 = julia.get('x')
    then:
      rsp1 == null
      rsp2 == null
  }

  def "get array variables"() {
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

  def "put variables"() {
    when:
      julia.set('x', v)
      def rsp = julia.get('x')
    then:
      rsp == v
    where:
      v                        | _
      7 as long                | _
      7 as int                 | _
      7 as short               | _
      7 as byte                | _
      7 as double              | _
      7 as float               | _
      Double.POSITIVE_INFINITY | _
      Float.NaN                | _
      '77'                     | _
      'αβγ'                    | _
      '7\n7'                   | _
      '7"7"'                   | _
  }

  def "put array variables"() {
    when:
      julia.set('x', v)
      def rsp = julia.get('x')
    then:
      rsp.data == v
      rsp.dims == [v.length]
    where:
      v                        | _
      [1,2,3]  as long[]       | _
      [1,2,3]  as int[]        | _
      [1,2,3]  as short[]      | _
      [1,2,3]  as byte[]       | _
      [1,2,3]  as double[]     | _
      [1,2,3]  as float[]      | _
  }

  def "put 2D array variables"() {
    when:
      julia.set('x', v)
      def rsp = julia.get('x')
    then:
      rsp.data == v.data
      rsp.dims == v.dims
    where:
      v                                              | _
      new LongArray(data: [1,2,3,4], dims: [2,2])    | _
      new IntegerArray(data: [1,2,3,4], dims: [2,2]) | _
      new ShortArray(data: [1,2,3,4], dims: [2,2])   | _
      new ByteArray(data: [1,2,3,4], dims: [2,2])    | _
      new DoubleArray(data: [1,2,3,4], dims: [2,2])  | _
      new FloatArray(data: [1,2,3,4], dims: [2,2])   | _
  }

}
