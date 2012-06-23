/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nativelibs4java.opencl;
import com.nativelibs4java.opencl.library.OpenCLLibrary.cl_event;
import org.bridj.*;
import static org.bridj.Pointer.*;
/**
 *
 * @author ochafik
 */
class ReusablePointers {

    public final ReusablePointer
            sizeT3_1 = new ReusablePointer(3 * SizeT.SIZE),
            sizeT3_2 = new ReusablePointer(3 * SizeT.SIZE),
            sizeT3_3 = new ReusablePointer(3 * SizeT.SIZE);
    
    public final ReusablePointer
            kernelArg = new ReusablePointer(8 * 16); // double16 arguments !
    
    public final Pointer<cl_event> event_out = allocateTypedPointer(cl_event.class);
    
    public final Pointer<Integer> pErr = allocateInt();
    
    public final int[] event_count = new int[1];
    public final ReusablePointer events_in = new ReusablePointer(Pointer.SIZE * 10);
    
    private ReusablePointers() {}
    
    public static ReusablePointers get() {
        return local.get();
    }
    private static final ThreadLocal<ReusablePointers> local = new ThreadLocal<ReusablePointers>() {

        @Override
        protected ReusablePointers initialValue() {
            return new ReusablePointers();
        }
        
    };
    
}
