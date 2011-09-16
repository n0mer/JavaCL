package com.nativelibs4java.opencl.library;
import org.bridj.Pointer;
import org.bridj.StructObject;
import org.bridj.ann.Field;
import org.bridj.ann.Library;
import org.bridj.ann.Ptr;
/**
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> or <a href="http://bridj.googlecode.com/">BridJ</a> .
 */
@Library("OpenCL") 
public class cl_buffer_region extends StructObject {
	public cl_buffer_region() {
		super();
	}
	public cl_buffer_region(Pointer pointer) {
		super(pointer);
	}
	@Ptr 
	@Field(0) 
	public long origin() {
		return this.io.getSizeTField(this, 0);
	}
	@Ptr 
	@Field(0) 
	public cl_buffer_region origin(long origin) {
		this.io.setSizeTField(this, 0, origin);
		return this;
	}
	@Ptr 
	@Field(1) 
	public long size() {
		return this.io.getSizeTField(this, 1);
	}
	@Ptr 
	@Field(1) 
	public cl_buffer_region size(long size) {
		this.io.setSizeTField(this, 1, size);
		return this;
	}
}
