#version 410 core
uniform mat4 projection;
uniform mat4 inverse_transform;
in highp vec3 point;
out VS_OUT
{
  highp vec3 direction;
  highp vec3 origin;
} vs_out;
void main()
{
  vs_out.direction = (inverse_transform * vec4(point, 0)).xyz;
  vs_out.origin = (inverse_transform * vec4(0, 0, 0, 1)).xyz;
  gl_Position = projection * vec4(point, 1);
}
