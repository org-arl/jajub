package org.arl.jajub;

/**
 * Base class representing multi-dimensional Julia arrays.
 * <p>
 * data is stored in column-major format in an attribute called
 * <code>data</code> in subclasses. For complex values, data is stored
 * in alternate real/imaginary format. The data length should be
 * the product of all dimensions, with an additional factor of 2x
 * for complex data.
 * <p>
 * Special arrays with no dimensions are considered scalars. These are
 * used to box complex scalar values in Java.
 */
public class NumericArray {

  /**
   * Dimensions of the array.
   */
  public int[] dims;

  /**
   * Indication that complex values stored in the data attribute.
   */
  public boolean isComplex;

}
