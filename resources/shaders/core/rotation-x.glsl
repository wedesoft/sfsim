#version 450 core


mat3 rotation_x(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(1,  0, 0,
              0,  c, s,
              0, -s, c);
}
