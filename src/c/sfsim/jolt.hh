#ifdef __cplusplus
extern "C" {
#endif
  void jolt_init(void);
  void jolt_destroy(void);

  extern const unsigned short int NON_MOVING;
  extern const unsigned short int MOVING;
  extern const unsigned short int NUM_LAYERS;

  typedef struct {
    double x, y, z;
  } Vec3;

  typedef struct {
    double real, imag, jmag, kmag;
  } Quaternion;

  typedef struct {
    double m00, m01, m02, m10, m11, m12, m20, m21, m22;
  } Mat3x3;

  typedef struct {
    double m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;
  } Mat4x4;

  void set_gravity(Vec3 gravity);
  void optimize_broad_phase();
  void update_system(double dt, int collision_steps);
  int create_and_add_dynamic_body(void *shape_settings, Vec3 center, Quaternion rotation);
  int create_and_add_static_body(void *shape_settings, Vec3 center, Quaternion rotation);
  void remove_and_destroy_body(int id);
  void *sphere_settings(float radius, float density);
  void *box_settings(Vec3 half_extent, float density);
  void *mesh_settings(float *vertices, int num_vertices, int *triangles, int num_triangles, float mass);
  void *convex_hull_settings(float *vertices, int num_vertices, float convex_radius, float density);
  void *static_compound_settings(void);
  void static_compound_add_shape(void *static_compound_settings, Vec3 position, Quaternion rotation, void *shape_settings);
  void set_friction(int id, float friction);
  void set_restitution(int id, float restitution);
  float get_mass(int id);
  Mat3x3 get_inertia(int id);
  Vec3 get_center_of_mass(int id);
  void add_force(int id, Vec3 force);
  void add_torque(int id, Vec3 torque);
  void add_impulse(int id, Vec3 impulse);
  void add_angular_impulse(int id, Vec3 angular_impulse);
  void activate_body(int id);
  Vec3 get_translation(int id);
  void set_translation(int id, Vec3 translation);
  Mat3x3 get_rotation(int id);
  Quaternion get_orientation(int id);
  void set_orientation(int id, Quaternion orientation);
  Vec3 get_linear_velocity(int id);
  void set_linear_velocity(int id, Vec3 velocity);
  Vec3 get_angular_velocity(int id);
  void set_angular_velocity(int id, Vec3 velocity);
  void *make_wheel_settings(Vec3 position, float width, float radius, float inertia, float angular_damping, Vec3 up, Vec3 forward,
      float suspension_min_length, float suspension_max_length, float stiffness, float damping, float max_brake_torque);
  void destroy_wheel_settings(void *wheel_settings);
  void *make_vehicle_constraint_settings(Vec3 up, Vec3 forward);
  void vehicle_constraint_settings_add_wheel(void *constraint, void *wheel_settings);
  void *create_and_add_vehicle_constraint(int body_id, Vec3 world_up, void *vehicle_constraint_settings);
  void set_brake_input(void *constraint, float brake_input);
  Mat4x4 get_wheel_local_transform(void *constraint, int wheel_index, Vec3 right, Vec3 up);
  float get_suspension_length(void *constraint, int wheel_index);
  float get_wheel_rotation_angle(void *constraint, int wheel_index);
  char has_hit_hard_point(void *constraint, int wheel_index);
  void remove_and_destroy_constraint(void *constraint);
#ifdef __cplusplus
}
#endif
