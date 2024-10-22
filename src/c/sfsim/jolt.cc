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
#include <Jolt/Physics/Collision/Shape/SphereShape.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/Shape/MeshShape.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
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
JPH::BodyInterface *body_interface = nullptr;

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
  body_interface = &physics_system->GetBodyInterface();
}

void jolt_destroy(void)
{
  body_interface = nullptr;
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

void update_system(double dt)
{
  const int cCollisionSteps = 1;
  physics_system->Update(dt, cCollisionSteps, temp_allocator, job_system);
}

void set_gravity(Vec3 gravity)
{
  JPH::Vec3 gravity_vector(gravity.x, gravity.y, gravity.z);
  physics_system->SetGravity(gravity_vector);
}

void optimize_broad_phase()
{
  physics_system->OptimizeBroadPhase();
}

void body_default_settings(JPH::BodyCreationSettings &body_settings)
{
  body_settings.mApplyGyroscopicForce = true;
  body_settings.mLinearDamping = 0.0;
  body_settings.mAngularDamping = 0.0;
}

int make_sphere(float radius, float density, Vec3 center, Quaternion rotation, Vec3 linear_velocity, Vec3 angular_velocity)
{
  JPH::SphereShape *sphere_shape = new JPH::SphereShape(radius);
  sphere_shape->SetDensity(density);
  JPH::RVec3 position(center.x, center.y, center.z);
  JPH::Quat orientation(rotation.imag, rotation.jmag, rotation.kmag, rotation.real);
  JPH::BodyCreationSettings sphere_settings(sphere_shape, position, orientation, JPH::EMotionType::Dynamic, MOVING);
  body_default_settings(sphere_settings);
  JPH::BodyID sphere_id = body_interface->CreateAndAddBody(sphere_settings, JPH::EActivation::Activate);
  JPH::Vec3 linear_speed(linear_velocity.x, linear_velocity.y, linear_velocity.z);
  body_interface->SetLinearVelocity(sphere_id, linear_speed);
  JPH::Vec3 angular_speed(angular_velocity.x, angular_velocity.y, angular_velocity.z);
  body_interface->SetAngularVelocity(sphere_id, angular_speed);
  return sphere_id.GetIndexAndSequenceNumber();
}

int make_box(Vec3 half_extent, float density, Vec3 center, Quaternion rotation, Vec3 linear_velocity, Vec3 angular_velocity)
{
  JPH::Vec3 half_extent_box(half_extent.x, half_extent.y, half_extent.z);
  JPH::BoxShapeSettings box_shape_settings(half_extent_box);
  box_shape_settings.mConvexRadius = 0.01f;
  box_shape_settings.SetDensity(density);
  box_shape_settings.SetEmbedded();
  JPH::ShapeSettings::ShapeResult shape_result = box_shape_settings.Create();
  JPH::ShapeRefC box_shape = shape_result.Get();
  JPH::RVec3 position(center.x, center.y, center.z);
  JPH::Quat orientation(rotation.imag, rotation.jmag, rotation.kmag, rotation.real);
  JPH::BodyCreationSettings box_settings(box_shape, position, orientation, JPH::EMotionType::Dynamic, MOVING);
  body_default_settings(box_settings);
  JPH::BodyID box_id = body_interface->CreateAndAddBody(box_settings, JPH::EActivation::Activate);
  JPH::Vec3 linear_speed(linear_velocity.x, linear_velocity.y, linear_velocity.z);
  body_interface->SetLinearVelocity(box_id, linear_speed);
  JPH::Vec3 angular_speed(angular_velocity.x, angular_velocity.y, angular_velocity.z);
  body_interface->SetAngularVelocity(box_id, angular_speed);
  return box_id.GetIndexAndSequenceNumber();
}

int make_mesh(float *vertices, int num_vertices, int *triangles, int num_triangles, float mass, Vec3 center, Quaternion rotation)
{
  JPH::VertexList vertex_list(num_vertices);
  for (int i=0; i<num_vertices; i++) {
    vertex_list[i] = JPH::Float3(vertices[0], vertices[1], vertices[2]);
    vertices += 3;
  };
  JPH::IndexedTriangleList triangle_list(num_triangles);
  for (int i=0; i<num_triangles; i++) {
    triangle_list[i] = JPH::IndexedTriangle(triangles[0], triangles[1], triangles[2], 0);
    triangles += 3;
  };
  JPH::PhysicsMaterialList materials { JPH::PhysicsMaterial::sDefault };
  JPH::MeshShapeSettings mesh_shape_settings(vertex_list, triangle_list, materials);
  mesh_shape_settings.SetEmbedded();
  JPH::ShapeSettings::ShapeResult shape_result = mesh_shape_settings.Create();
  JPH::ShapeRefC mesh_shape = shape_result.Get();
  JPH::RVec3 position(center.x, center.y, center.z);
  JPH::Quat orientation(rotation.imag, rotation.jmag, rotation.kmag, rotation.real);
  JPH::BodyCreationSettings mesh_settings(mesh_shape, position, orientation, JPH::EMotionType::Dynamic, MOVING);
  JPH::BodyID mesh_id = body_interface->CreateAndAddBody(mesh_settings, JPH::EActivation::Activate);
  return mesh_id.GetIndexAndSequenceNumber();
}

void set_friction(int id, float friction)
{
  JPH::BodyID body_id(id);
  body_interface->SetFriction(body_id, friction);
}

void set_restitution(int id, float restitution)
{
  JPH::BodyID body_id(id);
  body_interface->SetRestitution(body_id, restitution);
}

void remove_and_destroy_body(int id)
{
  JPH::BodyID body_id(id);
  body_interface->RemoveBody(body_id);
  body_interface->DestroyBody(body_id);
}

Vec3 get_translation(int id)
{
  JPH::BodyID body_id(id);
  JPH::RMat44 transform = body_interface->GetWorldTransform(body_id);
  JPH::RVec3 position = transform.GetTranslation();
  return (Vec3){ .x = position.GetX(), .y = position.GetY(), .z = position.GetZ() };
}

Mat3x3 get_rotation(int id)
{
  JPH::BodyID body_id(id);
  JPH::RMat44 transform = body_interface->GetWorldTransform(body_id);
  JPH::Vec3 x = transform.GetAxisX();
  JPH::Vec3 y = transform.GetAxisY();
  JPH::Vec3 z = transform.GetAxisZ();
  return (Mat3x3){
    .m00 = x.GetX(),
    .m01 = y.GetX(),
    .m02 = z.GetX(),
    .m10 = x.GetY(),
    .m11 = y.GetY(),
    .m12 = z.GetY(),
    .m20 = x.GetZ(),
    .m21 = y.GetZ(),
    .m22 = z.GetZ()
  };
}

Vec3 get_linear_velocity(int id)
{
  JPH::BodyID body_id(id);
  JPH::Vec3 linear_velocity = body_interface->GetLinearVelocity(body_id);
  return (Vec3){ .x = linear_velocity.GetX(), .y = linear_velocity.GetY(), .z = linear_velocity.GetZ() };
}

Vec3 get_angular_velocity(int id)
{
  JPH::BodyID body_id(id);
  JPH::Vec3 angular_velocity = body_interface->GetAngularVelocity(body_id);
  return (Vec3){ .x = angular_velocity.GetX(), .y = angular_velocity.GetY(), .z = angular_velocity.GetZ() };
}
