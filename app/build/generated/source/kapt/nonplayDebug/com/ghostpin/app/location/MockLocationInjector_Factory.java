package com.ghostpin.app.location;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class MockLocationInjector_Factory implements Factory<MockLocationInjector> {
  private final Provider<Context> contextProvider;

  public MockLocationInjector_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MockLocationInjector get() {
    return newInstance(contextProvider.get());
  }

  public static MockLocationInjector_Factory create(
      javax.inject.Provider<Context> contextProvider) {
    return new MockLocationInjector_Factory(Providers.asDaggerProvider(contextProvider));
  }

  public static MockLocationInjector_Factory create(Provider<Context> contextProvider) {
    return new MockLocationInjector_Factory(contextProvider);
  }

  public static MockLocationInjector newInstance(Context context) {
    return new MockLocationInjector(context);
  }
}
