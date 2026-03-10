package com.ghostpin.app.ui;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SimulationViewModel_Factory implements Factory<SimulationViewModel> {
  @Override
  public SimulationViewModel get() {
    return newInstance();
  }

  public static SimulationViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SimulationViewModel newInstance() {
    return new SimulationViewModel();
  }

  private static final class InstanceHolder {
    static final SimulationViewModel_Factory INSTANCE = new SimulationViewModel_Factory();
  }
}
