package mockit.asm.methodParameters;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mockit.asm.constantPool.ConstantPoolGeneration;
import mockit.asm.util.ByteVector;

/**
 * A visitor to visit a Java method parameter.
 */
public class MethodParameterVisitor {
    /**
     * The constant pool to which this method parameter must be added.
     */
    @Nonnull private final ConstantPoolGeneration cp;
    
    /**
     * The method parameter values in bytecode form. This byte vector only contains the values themselves, i.e. the number of values must be
     * stored as an unsigned byte just before these bytes.
     */
    @Nonnull private final ByteVector bv;
    
    /**
     * Next method parameter visitor. This field is used to store method parameter lists.
     */
    @Nullable private MethodParameterVisitor next;

    /**
     * Previous method parameter visitor. This field is used to store method parameter lists.
     */
    @Nullable private MethodParameterVisitor prev;
    
    public MethodParameterVisitor(@Nonnull ConstantPoolGeneration cp, @Nonnull String name, @Nonnull int access_flags) {
       this.cp = cp;
       bv = new ByteVector();
       bv.putShort(cp.newUTF8(name));
       bv.putShort(access_flags);
    }
    
    @Nonnegative
    private int getByteLength() { return bv.getLength(); }
    
    /**
     * Sets the visitor to the {@link #next} method parameter.
     */
    public void setNext(@Nullable MethodParameterVisitor next) { this.next = next; }
    
    /**
     * Returns the size of this method parameter list.
     */
    @Nonnegative
    public int getSize() {
       int size = 0;
       MethodParameterVisitor methodParameter = this;

       while (methodParameter != null) {
          size += methodParameter.getByteLength();
          methodParameter = methodParameter.next;
       }

       return size;
    }
    
    /**
     * Puts the method parameters of this method parameter writer list into the given byte vector.
     */
    public void put(@Nonnull ByteVector out) {
       MethodParameterVisitor aw = this;
       MethodParameterVisitor last = null;
       int n = 0;
       int size = 1;

       while (aw != null) {
          n++;
          size += aw.getByteLength();
          aw.prev = last;
          last = aw;
          aw = aw.next;
       }

       out.putInt(size);
       out.putByte(n);
       putFromLastToFirst(out, last);
    }
    
    private static void putFromLastToFirst(@Nonnull ByteVector out, @Nullable MethodParameterVisitor aw) {
       while (aw != null) {
          out.putByteVector(aw.bv);
          aw = aw.prev;
       }
    }
}
