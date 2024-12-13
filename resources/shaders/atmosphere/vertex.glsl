#version 410 core

uniform mat4 inverse_projection;
uniform mat4 camera_to_world;
in vec2 ndc;

out VS_OUT
{
  vec3 direction;
} vs_out;

// Simple vertex shader passing through coordinates of background quad for rendering the atmosphere.
void main()
{
  vec4 point = inverse_projection * vec4(ndc, 0.0, 1.0);
  vs_out.direction = (camera_to_world * vec4(point.xyz, 0)).xyz;
  gl_Position = vec4(ndc, 0.0, 1.0);
}
