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

  int make_sphere(float radius, Vec3 center);
  void remove_and_destroy_body(int id);

  Vec3 get_translation(int id);
#ifdef __cplusplus
}
#endif
