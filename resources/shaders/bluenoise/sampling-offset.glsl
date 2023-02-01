#version 410 core

uniform sampler2D bluenoise;
uniform int noise_size;

float sampling_offset()
{
  return texture(bluenoise, vec2(gl_FragCoord.x / noise_size, gl_FragCoord.y / noise_size)).r;
}
