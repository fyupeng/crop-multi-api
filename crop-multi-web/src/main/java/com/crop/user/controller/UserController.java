package com.crop.user.controller;

import com.crop.pojo.User;
import com.crop.pojo.UserInfo;
import com.crop.pojo.vo.UserForUpdateVO;
import com.crop.pojo.vo.UserInfoVO;
import com.crop.service.UserService;
import com.crop.utils.CropJSONResult;
import com.crop.utils.MD5Utils;
import io.swagger.annotations.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping(value = "/user")
@Api(value = "用户相关业务的接口", tags = {"用户相关业务的controller"})
public class UserController extends BasicController{

    @Autowired
    private UserService userService;

    @GetMapping(value = "/pingNetWork")
    @ApiOperation(value = "测试网络环境", notes = "测试网络环境的接口")
    public CropJSONResult pingNetWork() {
        return CropJSONResult.build(200, "ping successful!", null);
    }

    @ApiOperation(value = "查询用户信息", notes = "查询用户信息的接口")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")
    @PostMapping(value = "/query")
    public CropJSONResult query(String userId) {

        if(StringUtils.isBlank(userId)){
            return CropJSONResult.errorMsg("用户id不能为空");
        }

        UserInfo userInfo = userService.queryUserInfo(userId);
        UserInfoVO userInfoVO = new UserInfoVO();

        if (userInfo != null) {
            BeanUtils.copyProperties(userInfo,userInfoVO);
            User user = userService.queryUser(userId);
            userInfoVO.setPermission(user.getPermission());
        }


        return CropJSONResult.ok(userInfoVO);
    }



    @ApiOperation(value = "完善个人信息 - id字段请忽略", notes = "完善个人信息的接口")
    @ApiImplicitParam(name = "userInfo", value = "用户详情", required = true, dataType = "UserInfo", paramType = "body")
    @PostMapping(value = "/completeUserInfo")
    public CropJSONResult completeUserInfo(@RequestBody UserInfo userInfo) {

        if (StringUtils.isBlank(userInfo.getUserId())) {
            return CropJSONResult.errorMsg("用户id不能为空");
        }

        UserInfo userInfoByUserId = userService.queryUserInfo(userInfo.getUserId());
        if (userInfoByUserId == null) {
            return CropJSONResult.errorMsg("用户id不存在");
        }

        // id 是唯一标识符，不可更改
        userInfo.setId(userInfoByUserId.getId());

        userService.updateUserInfo(userInfo);

        return CropJSONResult.ok();
    }


    @ApiOperation(value = "用户上传头像", notes = "用户上传头像的接口")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "form")
    @PostMapping(value = "/uploadFace", headers = "content-type=multipart/form-data")
    public CropJSONResult uploadFace(@RequestParam(value = "userId") String userId,
                                     @RequestParam(value = "file") /* 这两个注解不能搭配使用，会导致 文件上传按钮失效*/
                                     /*@ApiParam(value = "头像")*/ MultipartFile file) throws Exception{

        if(StringUtils.isBlank(userId)){
            return CropJSONResult.errorMsg("用户id不能为空");
        }

        if (!userService.queryUserIdIsExist(userId)) {
            return CropJSONResult.errorMsg("用户id不存在");
        }

        //保存到数据库中的相对路径
        String uploadPathDB = "/" + userId + "/face";


        FileOutputStream fileOutputStream = null;
        InputStream inputStream = null;


        try {
            if(file != null){
                String fileName = file.getOriginalFilename();
                if (StringUtils.isNotBlank(fileName)) {
                    //文件上传的最终保存路径
                    String finalFacePath = FILE_SPACE + uploadPathDB + "/" + fileName;
                    //设置数据库保存的路径
                    uploadPathDB += ("/" + fileName);

                    File outFile = new File(finalFacePath);
                    //创建用户文件夹
                    if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) {
                        //创建父文件夹
                        outFile.getParentFile().mkdirs();
                    }

                    fileOutputStream = new FileOutputStream(outFile);
                    inputStream = file.getInputStream();
                    IOUtils.copy(inputStream, fileOutputStream);//把输入流赋值给输出流，就是把图片复制到输出流对应的路径下
                }
            }else {
                return CropJSONResult.errorMsg("上传出错1....");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return CropJSONResult.errorMsg("上传出错2....");
        }finally {
            if(fileOutputStream != null){
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        }


        /**
         * User 与 UserInfo 是 一一对应的关系，UserInfo 有两个候选键 id 和 userId
         */

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setAvatar(uploadPathDB);

        userService.updateUserInfo(userInfo);

        return CropJSONResult.ok(uploadPathDB);
    }

    @ApiOperation(value = "用户修改密码", notes = "用户修改密码的接口")
    @ApiImplicitParam(name = "userVO", value = "用户id", required = true, dataType = "UserForUpdateVO", paramType = "body")
    @PostMapping(value = "/updatePassword")
    public CropJSONResult updatePassword(@RequestBody UserForUpdateVO user) throws Exception {

        if (StringUtils.isBlank(user.getUsername()) || StringUtils.isBlank(user.getOldPassword())) {
            return CropJSONResult.errorMsg("用户名和原密码不能为空");
        }

        if (StringUtils.isBlank(user.getNewPassword())) {
            return CropJSONResult.errorMsg("新密码不能为空");
        }

        User userResult = userService.queryUserForLogin(user.getUsername(), MD5Utils.getMD5Str(user.getOldPassword()));

        if (userResult == null) {
            CropJSONResult.errorMsg("用户名或原密码不正确");
        }

        User userForUpdate = new User();
        userForUpdate.setId(userResult.getId());
        userForUpdate.setUsername(user.getUsername());
        userForUpdate.setPassword(MD5Utils.getMD5Str(user.getNewPassword()));
        userForUpdate.setPermission(userResult.getPermission());

        boolean updateTrue = userService.updateUser(userForUpdate);

        userForUpdate.setPassword("");

        return updateTrue ? CropJSONResult.ok(userForUpdate) : CropJSONResult.errorMsg("修改失败");
    }

}
