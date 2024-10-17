#ifdef __cplusplus
extern "C" {
#endif
  void jolt_init(void);
  void jolt_destroy(void);
  extern const unsigned short int NON_MOVING;
  extern const unsigned short int MOVING;
  extern const unsigned short int NUM_LAYERS;
  int make_sphere(float radius);
  void remove_and_destroy_body(int id);
#ifdef __cplusplus
}
#endif
