#version 410 core


mat3 rotation_y(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(c,  0, -s,
              0,  1,  0,
              s,  0,  c);
}
