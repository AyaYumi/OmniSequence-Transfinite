package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OmniBatchProviderAdapterRegistryTest {
    private static final String A="test:batch_a", B="test:batch_b";
    @AfterEach void cleanup(){OmniBatchProviderAdapterRegistry.unregister(A);OmniBatchProviderAdapterRegistry.unregister(B);}
    @Test void priorityAndPatternMatchingPreserveOriginalIdentity(){
        var p=proxy(ICraftingProvider.class);var pattern=proxy(IPatternDetails.class);var other=proxy(IPatternDetails.class);
        var low=proxy(OmniBatchCraftingProvider.class);var high=proxy(OmniBatchCraftingProvider.class);
        OmniBatchProviderAdapterRegistry.register(A,1,c->c==p,c->low);
        OmniBatchProviderAdapterRegistry.register(B,2,(c,d)->c==p&&d==pattern,(c,d)->high);
        assertSame(high,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        assertSame(low,OmniBatchProviderAdapterRegistry.resolve(p,other));
        assertFalse(p instanceof OmniBatchCraftingProvider);
    }
    @Test void supportsDoesNotInstantiateCapability(){
        var p=proxy(ICraftingProvider.class);var pattern=proxy(IPatternDetails.class);var calls=new AtomicInteger();
        OmniBatchProviderAdapterRegistry.register(A,1,(c,d)->c==p,(c,d)->{calls.incrementAndGet();return proxy(OmniBatchCraftingProvider.class);});
        for(int i=0;i<10000;i++)assertTrue(OmniBatchProviderAdapterRegistry.supports(p,pattern));
        assertEquals(0,calls.get());OmniBatchProviderAdapterRegistry.resolve(p,pattern);assertEquals(1,calls.get());
    }
    @Test void nativeFallbackAndNullFactories(){
        var p=proxy(OmniBatchCraftingProvider.class);var pattern=proxy(IPatternDetails.class);
        assertSame(p,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        OmniBatchProviderAdapterRegistry.register(A,2,(c,d)->true,(c,d)->null);
        assertTrue(OmniBatchProviderAdapterRegistry.supports(p,pattern));assertSame(p,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        var adapter=proxy(OmniBatchCraftingProvider.class);
        OmniBatchProviderAdapterRegistry.register(B,1,c->true,c->adapter);
        assertSame(adapter,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
    }
    @Test void equalPriorityUsesStableIdAndReplacementInvalidatesTopology(){
        var p=proxy(ICraftingProvider.class);var pattern=proxy(IPatternDetails.class);var a=proxy(OmniBatchCraftingProvider.class);var b=proxy(OmniBatchCraftingProvider.class);
        long revision=OmniBatchProviderAdapterRegistry.revision();
        OmniBatchProviderAdapterRegistry.register(B,3,c->true,c->b);OmniBatchProviderAdapterRegistry.register(A,3,c->true,c->a);
        assertTrue(OmniBatchProviderAdapterRegistry.revision()>revision);assertSame(a,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        revision=OmniBatchProviderAdapterRegistry.revision();OmniBatchProviderAdapterRegistry.register(A,3,c->true,c->b);
        assertTrue(OmniBatchProviderAdapterRegistry.revision()>revision);assertSame(b,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        revision=OmniBatchProviderAdapterRegistry.revision();OmniBatchProviderAdapterRegistry.unregister("test:missing");assertEquals(revision,OmniBatchProviderAdapterRegistry.revision());
    }
    @Test void recursiveFactoryIsGuardedAndStateIsCleanedAfterThrow(){
        var p=proxy(ICraftingProvider.class);var pattern=proxy(IPatternDetails.class);var a=proxy(OmniBatchCraftingProvider.class);
        OmniBatchProviderAdapterRegistry.register(A,1,c->true,c->{assertNull(OmniBatchProviderAdapterRegistry.resolve(c,pattern));assertFalse(OmniBatchProviderAdapterRegistry.supports(c,pattern));return a;});
        assertSame(a,OmniBatchProviderAdapterRegistry.resolve(p,pattern));assertSame(a,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        OmniBatchProviderAdapterRegistry.register(A,1,c->true,c->{throw new IllegalStateException("expected");});
        assertThrows(IllegalStateException.class,()->OmniBatchProviderAdapterRegistry.resolve(p,pattern));
        OmniBatchProviderAdapterRegistry.register(A,1,c->true,c->a);assertSame(a,OmniBatchProviderAdapterRegistry.resolve(p,pattern));
    }
    @Test void absentAndInvalidArguments(){
        var p=proxy(ICraftingProvider.class);var pattern=proxy(IPatternDetails.class);
        assertNull(OmniBatchProviderAdapterRegistry.resolve(p,pattern));assertFalse(OmniBatchProviderAdapterRegistry.supports(p,pattern));
        assertNull(OmniBatchProviderAdapterRegistry.resolve(null,pattern));assertNull(OmniBatchProviderAdapterRegistry.resolve(p,null));
        assertThrows(IllegalArgumentException.class,()->OmniBatchProviderAdapterRegistry.register(" ",0,c->true,c->null));
        assertThrows(NullPointerException.class,()->OmniBatchProviderAdapterRegistry.register(A,0,(java.util.function.Predicate<ICraftingProvider>)null,c->null));
    }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type){
        return (T)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{type},(p,m,args)->{
            return switch(m.getName()){case "equals"->p==args[0];case "hashCode"->System.identityHashCode(p);case "toString"->"Test"+type.getSimpleName();default->m.getReturnType()==boolean.class?false:m.getReturnType()==long.class?0L:null;};
        });
    }
    private static ClassLoader getClassLoader(){return OmniBatchProviderAdapterRegistryTest.class.getClassLoader();}
}
