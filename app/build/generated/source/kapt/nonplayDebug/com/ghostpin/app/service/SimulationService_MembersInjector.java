package com.ghostpin.app.service;

import com.ghostpin.app.location.MockLocationInjector;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class SimulationService_MembersInjector implements MembersInjector<SimulationService> {
  private final Provider<MockLocationInjector> mockLocationInjectorProvider;

  public SimulationService_MembersInjector(
      Provider<MockLocationInjector> mockLocationInjectorProvider) {
    this.mockLocationInjectorProvider = mockLocationInjectorProvider;
  }

  public static MembersInjector<SimulationService> create(
      Provider<MockLocationInjector> mockLocationInjectorProvider) {
    return new SimulationService_MembersInjector(mockLocationInjectorProvider);
  }

  public static MembersInjector<SimulationService> create(
      javax.inject.Provider<MockLocationInjector> mockLocationInjectorProvider) {
    return new SimulationService_MembersInjector(Providers.asDaggerProvider(mockLocationInjectorProvider));
  }

  @Override
  public void injectMembers(SimulationService instance) {
    injectMockLocationInjector(instance, mockLocationInjectorProvider.get());
  }

  @InjectedFieldSignature("com.ghostpin.app.service.SimulationService.mockLocationInjector")
  public static void injectMockLocationInjector(SimulationService instance,
      MockLocationInjector mockLocationInjector) {
    instance.mockLocationInjector = mockLocationInjector;
  }
}
