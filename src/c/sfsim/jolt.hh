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
    double m00, m01, m02, m10, m11, m12, m20, m21, m22;
  } Mat3x3;

  int make_sphere(float radius, Vec3 center);
  void remove_and_destroy_body(int id);

  Vec3 get_translation(int id);
  Mat3x3 get_rotation(int id);
#ifdef __cplusplus
}
#endif
