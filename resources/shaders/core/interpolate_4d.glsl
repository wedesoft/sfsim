#version 410 core

vec4 convert_4d_index(vec4 idx, int size_w, int size_z, int size_y, int size_x);

vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx)
{
  vec4 pixel = idx * (vec4(size_w, size_z, size_y, size_x) - 1);
  vec2 frac = vec2(fract(pixel.z), fract(pixel.w));
  vec4 indices = convert_4d_index(pixel, size_w, size_z, size_y, size_x);
  return texture(table, indices.sp) * (1 - frac.s) * (1 - frac.t) +
         texture(table, indices.tp) *       frac.s * (1 - frac.t) +
         texture(table, indices.sq) * (1 - frac.s) *       frac.t +
         texture(table, indices.tq) *       frac.s *       frac.t;
}
