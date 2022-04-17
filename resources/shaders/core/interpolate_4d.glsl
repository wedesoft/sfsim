#version 410 core

vec4 convert_4d_index(vec4 idx, int size_w, int size_z, int size_y, int size_x);

vec4 interpolate_4d(sampler2D table, int size, vec4 idx)
{
  vec4 pixel = idx * (size - 1);
  vec2 frac = vec2(fract(pixel.z), fract(pixel.w));
  vec4 indices = convert_4d_index(pixel, size, size, size, size); // TODO: use different sizes
  return texture(table, indices.sp) * (1 - frac.s) * (1 - frac.t) +
         texture(table, indices.tp) *       frac.s * (1 - frac.t) +
         texture(table, indices.sq) * (1 - frac.s) *       frac.t +
         texture(table, indices.tq) *       frac.s *       frac.t;
}
