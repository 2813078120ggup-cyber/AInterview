package com.tyut.aiinterview.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    @Update("""
            UPDATE `role`
            SET role_name = #{roleName}, description = #{description}, status = #{status}, version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int updateWithVersion(Role role);

    @Update("UPDATE `role` SET version = version + 1 WHERE id = #{id} AND version = #{version}")
    int bumpVersion(@Param("id") Long id, @Param("version") Integer version);
}
