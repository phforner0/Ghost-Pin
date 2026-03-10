package com.ghostpin.app.di;

import com.ghostpin.engine.validation.TrajectoryValidator;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SimulationModule_ProvideTrajectoryValidatorFactory implements Factory<TrajectoryValidator> {
  @Override
  public TrajectoryValidator get() {
    return provideTrajectoryValidator();
  }

  public static SimulationModule_ProvideTrajectoryValidatorFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TrajectoryValidator provideTrajectoryValidator() {
    return Preconditions.checkNotNullFromProvides(SimulationModule.INSTANCE.provideTrajectoryValidator());
  }

  private static final class InstanceHolder {
    static final SimulationModule_ProvideTrajectoryValidatorFactory INSTANCE = new SimulationModule_ProvideTrajectoryValidatorFactory();
  }
}
