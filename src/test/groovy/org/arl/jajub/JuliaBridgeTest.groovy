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
      v                                                  | _
      new LongArray(data: [1,2,3,4,5,6], dims: [2,3])    | _
      new IntegerArray(data: [1,2,3,4,5,6], dims: [3,2]) | _
      new ShortArray(data: [1,2,3,4,5,6], dims: [2,3])   | _
      new ByteArray(data: [1,2,3,4,5,6], dims: [3,2])    | _
      new DoubleArray(data: [1,2,3,4,5,6], dims: [2,3])  | _
      new FloatArray(data: [1,2,3,4,5,6], dims: [3,2])   | _
  }

  def "call func without args"() {
    when:
      def v = julia.call('rand')
    then:
      v instanceof Double
  }

  def "call func with primitive args"() {
    when:
      def v1 = julia.call('round', 2.3f)
      def v2 = julia.call('round', 2.3d)
      def v3 = julia.call('min', 2.3d, 4.6d)
      def v4 = julia.call('round', julia.expr('Int32'), 2.3d)
      def v5 = julia.call('(*)', 'abc', 'def')
    then:
      v1 instanceof Float
      v1 == 2.0f
      v2 instanceof Double
      v2 == 2.0d
      v3 instanceof Double
      v3 == 2.3d
      v4 instanceof Integer
      v4 == 2
      v5 instanceof String
      v5 == 'abcdef'
  }

  def "call func with array args"() {
    when:
      def x = new IntegerArray(data: [1,2,3,4], dims: [4])
      def y = new IntegerArray(data: [1,2,3,4], dims: [2,2])
      def z = new IntegerArray(data: [1,2], dims: [2])
      def v1 = julia.call('sum', x)
      def v2 = julia.call('sum', y)
      def v3 = julia.call('sum', y, julia.expr('dims=1'))
      def v4 = julia.call('sum', y, julia.expr('dims=2'))
      def v5 = julia.call('(+).', y, z)
    then:
      v1 == 10
      v2 == 10
      v3.data == [3,7]
      v3.dims == [1,2]
      v4.data == [4,6]
      v4.dims == [2,1]
      v5.data == [2,4,4,6]
      v5.dims == [2,2]
  }

  def "eval"() {
    when:
      def v1 = julia.eval('1+2')
      def v2 = julia.eval(julia.expr('1+2.2'))
    then:
      v1 == 3
      v2 == 3.2
  }

  def "define and eval func"() {
    when:
      def code = '''
        function myfunc(x)
          if x < 5
            x + 1.0
          else
            x * 2
          end
        end
      '''
      julia.exec(code)
      def v1 = julia.eval('myfunc(2.3)')
      def v2 = julia.eval('myfunc(7.2)')
    then:
      v1 == 3.3
      v2 == 14.4
  }

}
