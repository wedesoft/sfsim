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

  void set_gravity(Vec3 gravity);
  void optimize_broad_phase();
  void update_system(double dt, int collision_steps);
  int make_sphere(float radius, float density, Vec3 center, Quaternion rotation, Vec3 linear_velocity, Vec3 angular_velocity);
  int make_box(Vec3 half_extent, float density, Vec3 center, Quaternion rotation, Vec3 linear_velocity, Vec3 angular_velocity);
  int make_mesh(float *vertices, int num_vertices, int *triangles, int num_triangles, float mass, Vec3 center, Quaternion rotation);
  void set_friction(int id, float friction);
  void set_restitution(int id, float restitution);
  void remove_and_destroy_body(int id);

  Vec3 get_translation(int id);
  void set_translation(int id, Vec3 translation);
  Mat3x3 get_rotation(int id);
  Quaternion get_orientation(int id);
  void set_orientation(int id, Quaternion orientation);
  Vec3 get_linear_velocity(int id);
  Vec3 get_angular_velocity(int id);
#ifdef __cplusplus
}
#endif
