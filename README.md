![](https://github.com/org-arl/jajub/workflows/CI/badge.svg)

Java-Julia bridge
=================

Introduction
------------

JaJuB is a Java-Julia bridge to enable Java applications to use functionality implemented as
Julia code. To run the Julia code, you require Julia to be installed on the target system, in
addition to the JRE. Julia is invoked as a separate process, and JaJuB maintains a link to Julia
over a pipe. An efficient protocol is used to transfer data to/fro between Java and Julia.

JaJuB is aimed at implementation of numerical functionality in Julia, and hence the focus of the
project is on providing compatibility across numerical types. Complex data types are not supported
at present. Supported data types:

* Integer primitives: `Int64`, `Int32`, `Int16`, `Int8`
* Floating point primitives: `Float64`, `Float32`
* Complex primitives: `Complex{T}` where `T` is an integer or floating point type
* Arrays: `Array{T,N}` for integer, floating point and complex `T`, and any `N`
* Other primitves: `String`, `Nothing`

Support & API Documentation
---------------------------

* [Project Home](http://github.com/org-arl/jajub)
* [API documentation](http://org-arl.github.io/jajub/javadoc/)
* [Issue Tracking](http://github.com/org-arl/jajub/issues)

Example code
------------

Example code is shown as Groovy code for brevity, but it should be easy to figure out the equivalent Java code:

```groovy
import org.arl.jajub.*

julia = new JuliaBridge()
julia.open()                    // optional (automatically opened on first use)

println( julia.getJuliaVersion() )

println( julia.eval("1 + 2") )          // eval returns the value of the expression
println( julia.exec("println(1+2)") )   // exec returns the output on stdout/stderr

// define a Julia function
julia.exec("""
  function myfunc(x, y)
    if x < y
      x + y
    else
      x - y
    end
  end
""")

// Julia code can alternatively be maintained in a separate file
// and loaded as a resource:
//   julia.exec(getClass().getResourceAsStream('/test.jl'))

// now you can call the function
println( julia.eval("myfunc(2.7, 4.2)") )

// use closures for a nicer syntax:
myfunc  = { a, b -> julia.call("myfunc", a, b) }
myfunc_ = { a, b -> julia.call("myfunc.", a, b) }   // with broadcast

// you could do the same with plain old methods or Java lambdas,
// or just directly use julia.call() everywhere

// call Julia function
println( myfunc(2, 4) )
println( myfunc(2.7f, 4.2f) )

// call function with Java arrays
double[] xx = [1.2, 2.3]
double[] yy = [3.4, 4.5]
def zz = myfunc_(xx, yy)
println( zz.dims )
println( zz.data )

// call function with Java matrices or tensors
def xxx = new DoubleArray(data: [1.2, 2.3, 3.4, 4.5], dims: [2, 2])
def yyy = new DoubleArray(data: [3.4, 4.5, 5.6, 6.7], dims: [2, 2])
def zzz = myfunc_(xxx, yyy)
println( zzz.dims )
println( zzz.data )

// call function with complex numbers
println( julia.call("abs", julia.complex(2.7, 4.2)) )

// pass in additional Julia expressions
zzz = julia.call("sum", xxx, julia.expr("dims=2"))
println( zzz.dims )
println( zzz.data )

// you could also transfer variables, execute commands, and get back results
julia.exec("using LinearAlgebra")
svd = { A ->
  julia.set("A", A)
  julia.exec("u, s, v = svd(A)")
  julia.get("s")
}
println( svd(xxx).data )

// shutdown Julia
julia.close()
```

Building
--------

* `gradle` to build the jar
* `gradle test` to run all regression tests (automated through Github actions CI)
* `gradle javadoc` to build the Java API documentation
* `gradle upload` to upload jars to Maven staging (requires credentials)

Maven Central dependency
------------------------

    <dependency>
      <groupId>com.github.org-arl</groupId>
      <artifactId>jajub</artifactId>
      <version>0.1.0</version>
    </dependency>

Contributing
------------

Contributions are always welcome! Clone, develop and do a pull request!

Try to stick to the coding style already in use in the repository. Additionally, some guidelines:

* [Commit message style](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#commits)

License
-------

JaJuB is licensed under the MIT license.
See [LICENSE.txt](http://github.com/org-arl/jajub/blob/master/LICENSE.txt) for more details.
