#version 410 core

uniform int shadow_size;

uniform mat4 inverse_transform;

<% (doseq [i (range n)] %>
uniform sampler3D opacity<%= i %>;
uniform sampler2D offset<%= i %>;
uniform mat4 shadow_map_matrix<%= i %>;
uniform float depth<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>
uniform float split<%= i %>;
<% ) %>

float opacity_lookup(sampler2D offsets, sampler3D layers, float depth, vec3 opacity_map_coords);
float opacity_cascade_lookup(vec4 point)
{
  float z = -(inverse_transform * point).z;
<% (doseq [i (range n)] %>
  if (z <= split<%= (inc i) %>) {
    vec4 map_coords = shadow_map_matrix<%= i %> * point;
    float texel_size = 1.0 / shadow_size;  // TODO: test this shit
    float result = 0.0;
    for (int y=-1; y<=1; y++)
      for (int x=-1; x<=1; x++)
        result += opacity_lookup(offset<%= i %>, opacity<%= i %>, depth<%= i %>, map_coords.xyz + vec3(x, y, 0) * texel_size);
    result /= 9.0;
    return result;
  };
<% ) %>
  return 1.0;
}
