package org.arl.jajub;

/**
 * Class representing opaque Julia expressions that should be passed
 * on to Julia without modification.
 */
public final class JuliaExpr {

  private String s;

  /**
   * Construct a Julia expression from a string.
   */
  public JuliaExpr(String s) {
    this.s = s;
  }

  /**
   * Returns a string representing a Julia expression.
   */
  @Override
  public String toString() {
    return s;
  }

}
