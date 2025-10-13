#version 410 core


mat3 rotation_z(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3( c, s, 0,
              -s, c, 0,
               0, 0, 1);
}
