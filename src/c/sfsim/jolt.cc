#include <iostream>
#include <cstdarg>
#include <Jolt/Jolt.h>
#include <Jolt/Core/Factory.h>
#include <Jolt/RegisterTypes.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Core/JobSystemThreadPool.h>
#include <Jolt/Physics/PhysicsSettings.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Collision/BroadPhase/BroadPhaseLayer.h>
#include "sfsim/jolt.hh"


static void TraceImpl(const char *inFMT, ...)
{
  va_list list;
  va_start(list, inFMT);
  char buffer[1024];
  vsnprintf(buffer, sizeof(buffer), inFMT, list);
  va_end(list);
  std::cerr << buffer << std::endl;
}

#ifdef JPH_ENABLE_ASSERTS
static bool AssertFailedImpl(const char *inExpression, const char *inMessage, const char *inFile, uint inLine)
{
  std::cerr << inFile << ":" << inLine << ": (" << inExpression << ") " << (inMessage != nullptr? inMessage : "") << std::endl;
  return true;
};
#endif

const unsigned short int NON_MOVING = 0;
const unsigned short int MOVING = 1;
const unsigned short int NUM_LAYERS = 2;

class ObjectLayerPairFilterImpl: public JPH::ObjectLayerPairFilter
{
  public:
    virtual bool ShouldCollide(JPH::ObjectLayer inObject1, JPH::ObjectLayer inObject2) const override
    {
      switch (inObject1)
      {
        case NON_MOVING:
          return inObject2 == MOVING;
        case MOVING:
          return true;
        default:
          JPH_ASSERT(false);
          return false;
      }
    }
};

namespace BroadPhaseLayers
{
  static constexpr JPH::BroadPhaseLayer NON_MOVING(0);
  static constexpr JPH::BroadPhaseLayer MOVING(1);
  static constexpr uint NUM_LAYERS(2);
};

class BPLayerInterfaceImpl final: public JPH::BroadPhaseLayerInterface
{
public:
  BPLayerInterfaceImpl()
  {
    mObjectToBroadPhase[NON_MOVING] = BroadPhaseLayers::NON_MOVING;
    mObjectToBroadPhase[MOVING] = BroadPhaseLayers::MOVING;
  }

  virtual uint GetNumBroadPhaseLayers() const override
  {
    return BroadPhaseLayers::NUM_LAYERS;
  }

  virtual JPH::BroadPhaseLayer GetBroadPhaseLayer(JPH::ObjectLayer inLayer) const override
  {
    JPH_ASSERT(inLayer < NUM_LAYERS);
    return mObjectToBroadPhase[inLayer];
  }

#if defined(JPH_EXTERNAL_PROFILE) || defined(JPH_PROFILE_ENABLED)
  virtual const char *GetBroadPhaseLayerName(JPH::BroadPhaseLayer inLayer) const override
  {
    switch ((JPH::BroadPhaseLayer::Type)inLayer)
    {
      case (JPH::BroadPhaseLayer::Type)BroadPhaseLayers::NON_MOVING: return "NON_MOVING";
      case (JPH::BroadPhaseLayer::Type)BroadPhaseLayers::MOVING: return "MOVING";
      default: JPH_ASSERT(false); return "INVALID";
    }
  }
#endif // JPH_EXTERNAL_PROFILE || JPH_PROFILE_ENABLED

private:
  JPH::BroadPhaseLayer mObjectToBroadPhase[NUM_LAYERS];
};

class ObjectVsBroadPhaseLayerFilterImpl: public JPH::ObjectVsBroadPhaseLayerFilter
{
public:
  virtual bool ShouldCollide(JPH::ObjectLayer inLayer1, JPH::BroadPhaseLayer inLayer2) const override
  {
    switch (inLayer1)
    {
    case NON_MOVING:
      return inLayer2 == BroadPhaseLayers::MOVING;
    case MOVING:
      return true;
    default:
      JPH_ASSERT(false);
      return false;
    }
  }
};

JPH::TempAllocatorMalloc *temp_allocator = nullptr;
JPH::JobSystemThreadPool *job_system = nullptr;

const uint cMaxBodies = 1024;
const uint cNumBodyMutexes = 0;
const uint cMaxBodyPairs = 1024;
const uint cMaxContactConstraints = 1024;

BPLayerInterfaceImpl *broad_phase_layer_interface = nullptr;
ObjectLayerPairFilterImpl *object_vs_object_layer_filter = nullptr;
ObjectVsBroadPhaseLayerFilterImpl *object_vs_broadphase_layer_filter = nullptr;

JPH::PhysicsSystem *physics_system = nullptr;

void jolt_init(void)
{
  JPH::RegisterDefaultAllocator();
  JPH::Trace = TraceImpl;
  JPH_IF_ENABLE_ASSERTS(AssertFailed = AssertFailedImpl;)
  JPH::Factory::sInstance = new JPH::Factory();
  JPH::RegisterTypes();

  temp_allocator = new JPH::TempAllocatorMalloc;
  job_system = new JPH::JobSystemThreadPool(JPH::cMaxPhysicsJobs, JPH::cMaxPhysicsBarriers, std::thread::hardware_concurrency() - 1);

  broad_phase_layer_interface = new BPLayerInterfaceImpl;
  object_vs_object_layer_filter = new ObjectLayerPairFilterImpl;
  object_vs_broadphase_layer_filter = new ObjectVsBroadPhaseLayerFilterImpl;

  physics_system = new JPH::PhysicsSystem;
  physics_system->Init(cMaxBodies, cNumBodyMutexes, cMaxBodyPairs, cMaxContactConstraints, *broad_phase_layer_interface,
                       *object_vs_broadphase_layer_filter, *object_vs_object_layer_filter);
}

void jolt_destroy(void)
{
  delete physics_system;
  physics_system = nullptr;

  delete object_vs_broadphase_layer_filter;
  object_vs_broadphase_layer_filter = nullptr;
  delete object_vs_object_layer_filter;
  object_vs_object_layer_filter = nullptr;
  delete broad_phase_layer_interface;
  broad_phase_layer_interface = nullptr;

  delete job_system;
  job_system = nullptr;
  delete temp_allocator;
  temp_allocator = nullptr;

  JPH::UnregisterTypes();
  delete JPH::Factory::sInstance;
  JPH::Factory::sInstance = nullptr;
}
