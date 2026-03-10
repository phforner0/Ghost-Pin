package com.ghostpin.app.di;

import com.ghostpin.engine.noise.LayeredNoiseModel;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
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
public final class SimulationModule_ProvideLayeredNoiseModelFactory implements Factory<LayeredNoiseModel> {
  @Override
  public LayeredNoiseModel get() {
    return provideLayeredNoiseModel();
  }

  public static SimulationModule_ProvideLayeredNoiseModelFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LayeredNoiseModel provideLayeredNoiseModel() {
    return Preconditions.checkNotNullFromProvides(SimulationModule.INSTANCE.provideLayeredNoiseModel());
  }

  private static final class InstanceHolder {
    static final SimulationModule_ProvideLayeredNoiseModelFactory INSTANCE = new SimulationModule_ProvideLayeredNoiseModelFactory();
  }
}
