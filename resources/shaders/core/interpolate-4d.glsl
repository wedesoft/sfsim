#version 450 core

vec4 make_2d_index_from_4d(vec4 idx, int size_w, int size_z, int size_y, int size_x);

// Perform 4D texture lookup using coordinates between 0 and 1 (2 dimensions explicitely interpolated).
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx)
{
  vec4 pixel = idx * (vec4(size_x, size_y, size_z, size_w) - 1);
  vec2 frac = vec2(fract(pixel.z), fract(pixel.w));
  vec4 indices = make_2d_index_from_4d(pixel, size_w, size_z, size_y, size_x);
  return texture(table, indices.sp) * (1 - frac.s) * (1 - frac.t) +
         texture(table, indices.tp) *       frac.s * (1 - frac.t) +
         texture(table, indices.sq) * (1 - frac.s) *       frac.t +
         texture(table, indices.tq) *       frac.s *       frac.t;
}
