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
  void update_system(double dt);
  int make_sphere(float radius, Vec3 center, Quaternion rotation, Vec3 linear_velocity, Vec3 angular_velocity);
  void remove_and_destroy_body(int id);

  Vec3 get_translation(int id);
  Mat3x3 get_rotation(int id);
  Vec3 get_linear_velocity(int id);
  Vec3 get_angular_velocity(int id);
#ifdef __cplusplus
}
#endif
