#version 410 core

uniform mat4 projection;
uniform mat4 transform;
uniform float z_near;
uniform float z_far;
in vec3 point;

out VS_OUT
{
  vec3 origin;
  vec3 direction;
} vs_out;

// Simple vertex shader passing through coordinates of background quad for rendering the atmosphere.
void main()
{
  vs_out.origin = (transform * vec4(point * z_near / z_far, 1)).xyz;
  vs_out.direction = (transform * vec4(point, 0)).xyz;
  gl_Position = projection * vec4(point, 1);
}
