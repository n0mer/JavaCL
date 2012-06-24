#parse("main/Header.vm")
package com.nativelibs4java.opencl;
import com.nativelibs4java.util.Pair;
import static com.nativelibs4java.opencl.CLException.error;
import static com.nativelibs4java.opencl.JavaCL.CL;
import static com.nativelibs4java.opencl.library.OpenCLLibrary.*;
import static com.nativelibs4java.util.NIOUtils.directBytes;
import static com.nativelibs4java.util.NIOUtils.directCopy;

import java.nio.ByteBuffer;

import com.nativelibs4java.opencl.library.cl_buffer_region;
import com.nativelibs4java.opencl.library.OpenCLLibrary.cl_event;
import com.nativelibs4java.opencl.library.OpenCLLibrary.cl_mem;
import org.bridj.*;
import java.nio.ByteOrder;
import java.nio.Buffer;
import com.nativelibs4java.util.NIOUtils;
import org.bridj.util.Utils;
import static org.bridj.Pointer.*;


/**
 * OpenCL Memory Buffer Object.<br/>
 * A buffer object stores a one-dimensional collection of elements.<br/>
 * Elements of a buffer object can be a scalar data type (such as an int, float), vector data type, or a user-defined structure.<br/>
 * @see CLContext
 * @author Olivier Chafik
 */
public class CLBuffer<T> extends CLMem {
	final Object owner;
    final PointerIO<T> io;
    
	CLBuffer(CLContext context, long byteCount, long entityPeer, Object owner, PointerIO<T> io) {
        super(context, byteCount, entityPeer);
		this.owner = owner;
        this.io = io;
	}
    
	public Class<T> getElementClass() {
        return Utils.getClass(io.getTargetType());
    }
	public int getElementSize() {
        return (int)io.getTargetSize();
    }
	public long getElementCount() {
        return getByteCount() / getElementSize();
    }
	public Pointer<T> map(CLQueue queue, MapFlags flags, CLEvent... eventsToWaitFor) throws CLException.MapFailure {
		return map(queue, flags, 0, getElementCount(), true, eventsToWaitFor).getFirst();
    }
	public Pointer<T> map(CLQueue queue, MapFlags flags, long offset, long length, CLEvent... eventsToWaitFor) throws CLException.MapFailure {
		return map(queue, flags, offset, length, true, eventsToWaitFor).getFirst();
    }
    
	public Pair<Pointer<T>, CLEvent> mapLater(CLQueue queue, MapFlags flags, CLEvent... eventsToWaitFor) throws CLException.MapFailure {
		return map(queue, flags, 0, getElementCount(), false, eventsToWaitFor);
    }
	public Pair<Pointer<T>, CLEvent> mapLater(CLQueue queue, MapFlags flags, long offset, long length, CLEvent... eventsToWaitFor) throws CLException.MapFailure {
		return map(queue, flags, offset, length, false, eventsToWaitFor);
    }
    
	public Pointer<T> read(CLQueue queue, CLEvent... eventsToWaitFor) {
        Pointer<T> out = allocateArray(io, getElementCount()).order(queue.getDevice().getKernelsDefaultByteOrder());
        read(queue, out, true, eventsToWaitFor);
		return out;
	}
	public Pointer<T> read(CLQueue queue, long offset, long length, CLEvent... eventsToWaitFor) {
		Pointer<T> out = allocateArray(io, getElementCount()).order(queue.getDevice().getKernelsDefaultByteOrder());
        read(queue, offset, length, out, true, eventsToWaitFor);
		return out;
	}

	protected void checkBounds(long offset, long length) {
		if (offset + length * getElementSize() > getByteCount())
			throw new IndexOutOfBoundsException("Trying to map a region of memory object outside allocated range");
	}

	/**
	 * Can be used to create a new buffer object (referred to as a sub-buffer object) from an existing buffer object.
	 * @param usage is used to specify allocation and usage information about the image memory object being created and is described in table 5.3 of the OpenCL spec.
	 * @param offset
	 * @param length
	 * @since OpenCL 1.1
	 * @return sub-buffer that is a "window" of this buffer starting at the provided offset, with the provided length
	 */
	public CLBuffer<T> createSubBuffer(Usage usage, long offset, long length) {
		try {
			int s = getElementSize();
			cl_buffer_region region = new cl_buffer_region().origin(s * offset).size(s * length);
			#declareReusablePtrsAndPErr()
		    cl_mem mem = CL.clCreateSubBuffer(getEntity(), usage.getIntFlags(), CL_BUFFER_CREATE_TYPE_REGION, pointerTo(region), pErr);
	        #checkPErr()
	        return mem == null ? null : new CLBuffer<T>(context, length * s, getPeer(mem), null, io);
		} catch (Throwable th) {
    		// TODO check if supposed to handle OpenCL 1.1
    		throw new UnsupportedOperationException("Cannot create sub-buffer (OpenCL 1.1 feature).", th);
    	}
	}
	
	/**
	 * enqueues a command to copy a buffer object identified by src_buffer to another buffer object identified by destination.
	 * @param destination
	 * @param eventsToWaitFor Events that need to complete before this particular command can be executed. Special value {@link CLEvent#FIRE_AND_FORGET} can be used to avoid returning a CLEvent.  
	 * @return event which indicates the copy operation has completed, or null if eventsToWaitFor contains {@link CLEvent#FIRE_AND_FORGET}.
	 */
	public CLEvent copyTo(CLQueue queue, CLMem destination, CLEvent... eventsToWaitFor) {
		return copyTo(queue, 0, getElementCount(), destination, 0, eventsToWaitFor);	
	}
	
	/**
	 * enqueues a command to copy a buffer object identified by src_buffer to another buffer object identified by destination.
	 * @param queue
	 * @param srcOffset
	 * @param length
	 * @param destination
	 * @param destOffset
	 * @param eventsToWaitFor Events that need to complete before this particular command can be executed. Special value {@link CLEvent#FIRE_AND_FORGET} can be used to avoid returning a CLEvent.  
	 * @return event which indicates the copy operation has completed, or null if eventsToWaitFor contains {@link CLEvent#FIRE_AND_FORGET}.
	 */
	public CLEvent copyTo(CLQueue queue, long srcOffset, long length, CLMem destination, long destOffset, CLEvent... eventsToWaitFor) {
		long 
			byteCount = getByteCount(),
			destByteCount = destination.getByteCount(),
			eltSize = getElementSize(),
			actualSrcOffset = srcOffset * eltSize, 
			actualDestOffset = destOffset * eltSize, 
			actualLength = length * eltSize;
		
		if (	actualSrcOffset < 0 ||
			actualSrcOffset >= byteCount ||
			actualSrcOffset + actualLength > byteCount ||
			actualDestOffset < 0 ||
			actualDestOffset >= destByteCount ||
			actualDestOffset + actualLength > destByteCount
		)
			throw new IndexOutOfBoundsException("Invalid copy parameters : srcOffset = " + srcOffset + ", destOffset = " + destOffset + ", length = " + length + " (element size = " + eltSize + ", source byte count = " + byteCount + ", destination byte count = " + destByteCount + ")"); 
		
		#declareReusablePtrsAndEventsInOut()
        error(CL.clEnqueueCopyBuffer(
			queue.getEntityPeer(),
			getEntityPeer(),
			destination.getEntityPeer(),
			actualSrcOffset,
			actualDestOffset,
			actualLength,
			#eventsInOutArgsRaw()
		));
		#returnEventOut("queue")
	}

	protected Pair<Pointer<T>, CLEvent> map(CLQueue queue, MapFlags flags, long offset, long length, boolean blocking, CLEvent... eventsToWaitFor) {
		checkBounds(offset, length);
		#declareReusablePtrsAndEventsInOutBlockable()
		#declarePErr()
        long mappedPeer = CL.clEnqueueMapBuffer(
			queue.getEntityPeer(), 
			getEntityPeer(), 
			blocking ? CL_TRUE : CL_FALSE,
			flags.value(),
			offset * getElementSize(),
            length * getElementSize(),
            #eventsInOutArgsRaw(),
			getPeer(pErr)
		);
		#checkPErr()
		if (mappedPeer == 0)
			return null;
        return new Pair<Pointer<T>, CLEvent>(
			pointerToAddress(mappedPeer, io).validElements(length).order(queue.getDevice().getKernelsDefaultByteOrder()),
			#eventOutWrapper("queue")
		);
    }

    public CLEvent unmap(CLQueue queue, Pointer<T> buffer, CLEvent... eventsToWaitFor) {
    	#declareReusablePtrsAndEventsInOut();
        error(CL.clEnqueueUnmapMemObject(queue.getEntityPeer(), getEntityPeer(), getPeer(buffer), #eventsInOutArgsRaw()));
		#returnEventOut("queue")
    }

    /**
     * @deprecated use {@link CLBuffer#read(CLQueue, Pointer, boolean, CLEvent[])} instead
     */
    @Deprecated
	public CLEvent read(CLQueue queue, Buffer out, boolean blocking, CLEvent... eventsToWaitFor) {
		return read(queue, 0, -1, out, blocking, eventsToWaitFor);
    }
    
	public CLEvent read(CLQueue queue, Pointer<T> out, boolean blocking, CLEvent... eventsToWaitFor) {
        return read(queue, 0, -1, out, blocking, eventsToWaitFor);
	}

	/**
     * @deprecated use {@link CLBuffer#read(CLQueue, long, long, Pointer, boolean, CLEvent[])} instead
     */
    @Deprecated
	public CLEvent read(CLQueue queue, long offset, long length, Buffer out, boolean blocking, CLEvent... eventsToWaitFor) {
		if (out == null)
			throw new IllegalArgumentException("Null output buffer !");
		
		if (out.isReadOnly())
            throw new IllegalArgumentException("Output buffer for read operation is read-only !");
        boolean indirect = !out.isDirect();
        Pointer<T> ptr = null;
		if (indirect) {
			ptr = allocateArray(io, length).order(queue.getDevice().getKernelsDefaultByteOrder());
			blocking = true;
		} else {
			ptr = (Pointer)pointerToBuffer(out);
        }
        CLEvent ret = read(queue, offset, length, ptr, blocking, eventsToWaitFor);
        if (indirect)
            NIOUtils.put(ptr.getBuffer(), out);
        
        return ret;
	}
	
	public CLEvent read(CLQueue queue, long offset, long length, Pointer<T> out, boolean blocking, CLEvent... eventsToWaitFor) {
		if (out == null)
			throw new IllegalArgumentException("Null output pointer !");
		
		if (length < 0) {
			if (isGL) {
				length = out.getValidElements();
			}
			if (length < 0) {
				length = getElementCount();
				long s = out.getValidElements();
				if (length > s && s >= 0)
					length = s;
			}
		}
		
		#declareReusablePtrsAndEventsInOutBlockable()
        error(CL.clEnqueueReadBuffer(
            queue.getEntityPeer(),
            getEntityPeer(),
            blocking ? CL_TRUE : 0,
            offset * getElementSize(),
            length * getElementSize(),
            getPeer(out),
            #eventsInOutArgsRaw()
        ));
        #returnEventOut("queue")
    }
    
	/**
     * @deprecated use {@link CLBuffer#write(CLQueue, Pointer, boolean, CLEvent[])} instead
     */
    @Deprecated
	public CLEvent write(CLQueue queue, Buffer in, boolean blocking, CLEvent... eventsToWaitFor) {
		return write(queue, 0, -1, in, blocking, eventsToWaitFor);
	}
	
	public CLEvent write(CLQueue queue, Pointer<T> in, boolean blocking, CLEvent... eventsToWaitFor) {
		return write(queue, 0, -1, in, blocking, eventsToWaitFor);
	}

	/**
     * @deprecated use {@link CLBuffer#write(CLQueue, long, long, Pointer, boolean, CLEvent[])} instead
     */
    @Deprecated
	public CLEvent write(CLQueue queue, long offset, long length, Buffer in, boolean blocking, CLEvent... eventsToWaitFor) {
		if (in == null)
			throw new IllegalArgumentException("Null input buffer !");
		
		boolean indirect = !in.isDirect();
        Pointer<T> ptr = null;
		if (indirect) {
			ptr = allocateArray(io, length).order(queue.getDevice().getKernelsDefaultByteOrder());
			ptr.setValues(in);
			blocking = true;
		} else {
			ptr = (Pointer)pointerToBuffer(in);
        }
        return write(queue, offset, length, ptr, blocking, eventsToWaitFor);
	}
	
	
	public CLEvent write(CLQueue queue, long offset, long length, Pointer<T> in, boolean blocking, CLEvent... eventsToWaitFor) {
		if (length == 0)
			return null;
		
		if (in == null)
			throw new IllegalArgumentException("Null input pointer !");
		
		if (length < 0) {
			if (isGL)
				length = in.getValidElements();
			if (length < 0) {
				length = getElementCount();
				long s = in.getValidElements();
				if (length > s && s >= 0)
					length = s;
			}
		}
		
		#declareReusablePtrsAndEventsInOutBlockable()
        error(CL.clEnqueueWriteBuffer(
            queue.getEntityPeer(),
            getEntityPeer(),
            blocking ? CL_TRUE : CL_FALSE,
            offset * getElementSize(),
            length * getElementSize(),
            getPeer(in),
            #eventsInOutArgsRaw()
        ));
        #returnEventOut("queue")
    }

    public CLEvent writeBytes(CLQueue queue, long offset, long length, ByteBuffer in, boolean blocking, CLEvent... eventsToWaitFor) {
    		return writeBytes(queue, offset, length, pointerToBuffer(in), blocking, eventsToWaitFor);
    }
    public CLEvent writeBytes(CLQueue queue, long offset, long length, Pointer<?> in, boolean blocking, CLEvent... eventsToWaitFor) {
        if (in == null)
			throw new IllegalArgumentException("Null input pointer !");
		
		#declareReusablePtrsAndEventsInOutBlockable()
        error(CL.clEnqueueWriteBuffer(
            queue.getEntityPeer(),
            getEntityPeer(),
            blocking ? CL_TRUE : 0,
            offset,
            length,
            getPeer(in),
            #eventsInOutArgsRaw()
        ));
        #returnEventOut("queue")
    }

    private <T extends CLMem> T copyGLMark(T mem) {
        mem.isGL = this.isGL;
        return mem;
    }
        
    public CLBuffer<T> emptyClone(CLMem.Usage usage) {
    		return (CLBuffer)getContext().createBuffer(usage, io, getElementCount());
    }
    
    #foreach ($prim in $primitivesNoBool)

	public CLBuffer<${prim.WrapperName}> asCL${prim.BufferName}() {
		return as(${prim.WrapperName}.class);
	}
	
	#end
	
	public <T> CLBuffer<T> as(Class<T> newTargetType) {
		long mem = getEntityPeer();
		error(CL.clRetainMemObject(mem));
        PointerIO<T> newIO = PointerIO.getInstance(newTargetType);
		return copyGLMark(new CLBuffer<T>(context, getByteCount(), mem, owner, newIO));
	}
	
}
