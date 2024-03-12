#version 410 core

uniform mat4 projection;
uniform mat4 object_to_world;
uniform mat4 object_to_camera;

in vec3 vertex;
in vec3 normal;
in vec2 texcoord;

out VS_OUT
{
  vec3 point;
  vec3 normal;
  vec2 texcoord;
} vs_out;

void main()
{
  vs_out.point = (object_to_world * vec4(vertex, 1)).xyz;
  vs_out.normal = mat3(object_to_world) * normal;
  vs_out.texcoord = texcoord;
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
}
