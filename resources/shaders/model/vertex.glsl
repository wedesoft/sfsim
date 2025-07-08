#version 410 core

uniform mat4 projection;
uniform mat4 object_to_world;
uniform mat4 object_to_camera;
<% (doseq [i (range num-scene-shadows)] %>
uniform mat4 object_to_shadow_map_<%= (inc ^long i) %>;
<% ) %>

in vec3 vertex;
<% (if bump %>
in vec3 tangent;
in vec3 bitangent;
<% ) %>
in vec3 normal;
<% (if (or textured bump) %>
in vec2 texcoord;
<% ) %>

out VS_OUT
{
  vec3 world_point;
  vec3 normal;
<% (if bump %>
  mat3 surface;
<% ) %>
<% (if (or textured bump) %>
  vec2 texcoord;
<% ) %>
<% (doseq [i (range num-scene-shadows)] %>
  vec4 object_shadow_pos_<%= (inc ^long i) %>;
<% ) %>
} vs_out;

void main()
{
  vs_out.world_point = (object_to_world * vec4(vertex, 1)).xyz;
  vs_out.normal = mat3(object_to_world) * normal;
<% (if bump %>
  vs_out.surface = mat3(object_to_world) * mat3(tangent, bitangent, normal);
<% ) %>
<% (if (or textured bump) %>
  vs_out.texcoord = texcoord;
<% ) %>
<% (doseq [i (range num-scene-shadows)] %>
  vs_out.object_shadow_pos_<%= (inc ^long i) %> = object_to_shadow_map_<%= (inc ^long i) %> * vec4(vertex, 1);
<% ) %>
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
}
