/*
 * (c) 2013 panter llc, Zurich, Switzerland.
 */
package ch.bergturbenthal.image.provider.util;

/**
 * TODO: add type comment.
 * 
 * @param <A>
 * @param <B>
 * @param <C>
 * @param <D>
 * 
 */
public class Quad<A, B, C, D> {
  public A first;
  public B second;
  public C third;
  public D fourth;

  public Quad(final A first, final B second, final C third, final D fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }

}
