/**
 * Copyright 2010 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
 
package com.jogamp.common.os;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.jogamp.common.jvm.JNILibLoaderBase;

/**
 * Provides bundling of:<br>
 * <ul>
 * <li>The to-be-glued native library, eg OpenGL32.dll. From hereon this is referred as the Tool.</li>
 * <li>The JNI glue-code native library, eg jogl_desktop.dll. From heron this is referred as the Glue</li>
 * </ul><br>
 * An instance provides a complete {@link com.jogamp.common.os.DynamicLookupHelper}
 * to {@link com.jogamp.gluegen.runtime.ProcAddressTable#reset(com.jogamp.common.os.DynamicLookupHelper) reset}
 * the {@link com.jogamp.gluegen.runtime.ProcAddressTable}.<br>
 * At construction, it:
 * <ul>
 *  <li> loads the Tool native library via
 *       {@link com.jogamp.common.os.NativeLibrary#open(java.lang.String, java.lang.ClassLoader, boolean) NativeLibrary's open method}</li>
 *  <li> loads the {@link com.jogamp.common.jvm.JNILibLoaderBase#loadLibrary(java.lang.String, java.lang.String[], boolean)  Glue native library}</li>
 *  <li> resolves the Tool's {@link com.jogamp.common.os.DynamicLibraryBundleInfo#getToolGetProcAddressFuncNameList() GetProcAddress}. (optional)</li>
 * </ul>
 */
public class DynamicLibraryBundle implements DynamicLookupHelper {
    protected static final boolean DEBUG = NativeLibrary.DEBUG;
    protected static final boolean DEBUG_LOOKUP = NativeLibrary.DEBUG_LOOKUP;

    private DynamicLibraryBundleInfo info;

    private List<List<String>> toolLibNames;
    private boolean[] toolLibLoaded;
    private int toolLibLoadedNumber;
    protected List<NativeLibrary> nativeLibraries;

    private List<String> glueLibNames;
    private boolean[] glueLibLoaded;
    private int glueLibLoadedNumber;

    private long toolGetProcAddressHandle;
    private HashSet<String> toolGetProcAddressFuncNameSet;
    private List<String> toolGetProcAddressFuncNameList;

    public DynamicLibraryBundle(DynamicLibraryBundleInfo info) {
        if(null==info) {
            throw new RuntimeException("Null DynamicLibraryBundleInfo");
        }
        this.info = info;
        if(DEBUG) {
            System.err.println(Thread.currentThread().getName()+" - DynamicLibraryBundle.init start with: "+info.getClass().getName());
            Thread.dumpStack();
        }
        nativeLibraries = new ArrayList<NativeLibrary>();
        toolLibNames = info.getToolLibNames();
        glueLibNames = info.getGlueLibNames();
        loadLibraries();
        toolGetProcAddressFuncNameList = info.getToolGetProcAddressFuncNameList();
        if(null!=toolGetProcAddressFuncNameList) {
            toolGetProcAddressFuncNameSet = new HashSet<String>(toolGetProcAddressFuncNameList);
            toolGetProcAddressHandle = getToolGetProcAddressHandle();
        } else {
            toolGetProcAddressFuncNameSet = new HashSet<String>();
            toolGetProcAddressHandle = 0;
        }
        if(DEBUG) {
            System.err.println("DynamicLibraryBundle.init Summary: "+info.getClass().getName());
            System.err.println("     toolGetProcAddressFuncNameList: "+toolGetProcAddressFuncNameList);
            System.err.println("     Tool Lib Names : "+toolLibNames);
            System.err.println("     Tool Lib Loaded: "+getToolLibLoadedNumber()+"/"+getToolLibNumber()+" "+Arrays.toString(toolLibLoaded)+", complete "+isToolLibComplete());
            System.err.println("     Glue Lib Names : "+glueLibNames);
            System.err.println("     Glue Lib Loaded: "+getGlueLibLoadedNumber()+"/"+getGlueLibNumber()+" "+Arrays.toString(glueLibLoaded)+", complete "+isGlueLibComplete());
            System.err.println("     All Complete: "+isLibComplete());
        }
    }

    public final boolean isLibComplete() {
        return isToolLibComplete() && isGlueLibComplete() ;
    }

    public final int getToolLibNumber() {
        return toolLibNames.size();
    }

    public final int getToolLibLoadedNumber() {
        return toolLibLoadedNumber;
    }

    /**
     * @return true if all tool libraries are loaded,
     *         otherwise false.
     *         
     * @see DynamicLibraryBundleInfo#getToolLibNames()
     */
    public final boolean isToolLibComplete() {
        return getToolLibNumber() == getToolLibLoadedNumber();
    }

    public final boolean isToolLibLoaded() {
        return 0 < toolLibLoadedNumber;
    }

    public final boolean isToolLibLoaded(int i) {
        if(0 <= i && i < toolLibLoaded.length) {
            return toolLibLoaded[i];
        }
        return false;
    }

    public final int getGlueLibNumber() {
        return glueLibNames.size();
    }

    public final int getGlueLibLoadedNumber() {
        return glueLibLoadedNumber;
    }

    /**
     * @return true if the last entry has been loaded,
     *         while ignoring the preload dependencies. 
     *         Otherwise false.
     *         
     * @see DynamicLibraryBundleInfo#getGlueLibNames()
     */
    public final boolean isGlueLibComplete() {
        return isGlueLibLoaded(getGlueLibNumber() - 1);
    }

    public final boolean isGlueLibLoaded(int i) {
        if(0 <= i && i < glueLibLoaded.length) {
            return glueLibLoaded[i];
        }
        return false;
    }

    public final DynamicLibraryBundleInfo getBundleInfo() { return info; }

    protected long getToolGetProcAddressHandle() {
        if(!isToolLibLoaded()) {
            return 0;
        }
        long aptr = 0;
        for (int i=0; i < toolGetProcAddressFuncNameList.size(); i++) {
            final String name = toolGetProcAddressFuncNameList.get(i);
            aptr = dynamicLookupFunctionOnLibs(name);
            if(DEBUG) {
                System.err.println("getToolGetProcAddressHandle: "+name+" -> 0x"+Long.toHexString(aptr));
            }
        }
        return aptr;
    }

    protected NativeLibrary loadFirstAvailable(List<String> libNames, ClassLoader loader, boolean global) {
        for (int i=0; i < libNames.size(); i++) {            
            final NativeLibrary lib = NativeLibrary.open(libNames.get(i), loader, global);
            if (lib != null) {
                return lib;
            }
        }
        return null;
    }

    private void loadLibraries() {
        if( null == toolLibNames || toolLibNames.size() == 0) {
            if(DEBUG) {
                System.err.println("No Tool native library names given");
            }
            return;
        }

        if( null == glueLibNames || glueLibNames.size() == 0 ) {
            if(DEBUG) {
                System.err.println("No Glue native library names given");
            }
            return;
        }

        toolLibLoadedNumber = 0;
        int i;
        toolLibLoaded = new boolean[toolLibNames.size()];
        for(i=0; i<toolLibNames.size(); i++) {
            toolLibLoaded[i] = false;
        }

        glueLibLoaded = new boolean[glueLibNames.size()];
        for(i=0; i<glueLibNames.size(); i++) {
            glueLibLoaded[i] = false;
        }

        ClassLoader loader = getClass().getClassLoader();
        NativeLibrary lib = null;

        for (i=0; i < toolLibNames.size(); i++) {    
            final List<String> libNames = toolLibNames.get(i);
            if( null != libNames && libNames.size() > 0 ) {
                lib = loadFirstAvailable(libNames, loader, info.shallLinkGlobal());
                if ( null == lib ) {
                    if(DEBUG) {
                        System.err.println("Unable to load any Tool library of: "+libNames);
                    }
                } else {
                    nativeLibraries.add(lib);
                    toolLibLoaded[i]=true;
                    toolLibLoadedNumber++;
                    if(DEBUG) {
                        System.err.println("Loaded Tool library: "+lib);
                    }
                }
            }
        }
        if( !isToolLibLoaded() ) {
            if(DEBUG) {
                System.err.println("No Tool libraries loaded");
            }
            return;
        }

        glueLibLoadedNumber = 0;
        for (i=0; i < glueLibNames.size(); i++) {    
            final String libName = glueLibNames.get(i);
            boolean ignoreError = true;
            boolean res;
            try {
                res = GlueJNILibLoader.loadLibrary(libName, ignoreError);
                if(DEBUG && !res) {
                    System.err.println("Info: Could not load JNI/Glue library: "+libName);
                }
            } catch (UnsatisfiedLinkError e) {
                res = false;
                if(DEBUG) {
                    System.err.println("Unable to load JNI/Glue library: "+libName);
                    e.printStackTrace();
                }
            }
            glueLibLoaded[i] = res;
            if(res) {
                glueLibLoadedNumber++;
            }
        }
    }

    private long dynamicLookupFunctionOnLibs(String funcName) {
        if(!isToolLibLoaded() || null==funcName) {
            if(DEBUG_LOOKUP && !isToolLibLoaded()) {
                System.err.println("Lookup-Native: <" + funcName + "> ** FAILED ** Tool native library not loaded");
            }
            return 0;
        }
        long addr = 0;
        NativeLibrary lib = null;

        if(info.shallLookupGlobal()) {
            // Try a global symbol lookup first ..
            addr = NativeLibrary.dynamicLookupFunctionGlobal(funcName);
        }
        // Look up this function name in all known libraries
        for (int i=0; 0==addr && i < nativeLibraries.size(); i++) {
            lib = nativeLibraries.get(i);
            addr = lib.dynamicLookupFunction(funcName);
        }
        if(DEBUG_LOOKUP) {
            String libName = ( null == lib ) ? "GLOBAL" : lib.toString();
            if(0!=addr) {
                System.err.println("Lookup-Native: <" + funcName + "> 0x" + Long.toHexString(addr) + " in lib " + libName );
            } else {
                System.err.println("Lookup-Native: <" + funcName + "> ** FAILED ** in libs " + nativeLibraries);
            }
        }
        return addr;
    }

    private long toolDynamicLookupFunction(String funcName) {
        if(0 != toolGetProcAddressHandle) {
            long addr = info.toolGetProcAddress(toolGetProcAddressHandle, funcName);
            if(DEBUG_LOOKUP) {
                if(0!=addr) {
                    System.err.println("Lookup-Tool: <"+funcName+"> 0x"+Long.toHexString(addr));
                }
            }
            return addr;
        }
        return 0;
    }
    
    public long dynamicLookupFunction(String funcName) {
        if(!isToolLibLoaded() || null==funcName) {
            if(DEBUG_LOOKUP && !isToolLibLoaded()) {
                System.err.println("Lookup: <" + funcName + "> ** FAILED ** Tool native library not loaded");
            }
            return 0;
        }

        if(toolGetProcAddressFuncNameSet.contains(funcName)) {
            return toolGetProcAddressHandle;
        }

        long addr = 0;
        final boolean useToolGetProcAdressFirst = info.useToolGetProcAdressFirst(funcName);

        if(useToolGetProcAdressFirst) {
            addr = toolDynamicLookupFunction(funcName);
        }
        if(0==addr) {
            addr = dynamicLookupFunctionOnLibs(funcName);
        }
        if(0==addr && !useToolGetProcAdressFirst) {
            addr = toolDynamicLookupFunction(funcName);            
        }
        return addr;
    }

    /** Inherit access */
    static class GlueJNILibLoader extends JNILibLoaderBase {
      protected static synchronized boolean loadLibrary(String libname, boolean ignoreError) {
        return JNILibLoaderBase.loadLibrary(libname, ignoreError);
      }
    }
}

