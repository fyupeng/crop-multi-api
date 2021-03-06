package com.crop.admin.controller;

import com.crop.pojo.Article;
import com.crop.pojo.Classfication;
import com.crop.pojo.Comment;
import com.crop.service.CommentService;
import com.crop.user.controller.BasicController;
import com.crop.pojo.User;
import com.crop.service.ArticleService;
import com.crop.service.ClassficationService;
import com.crop.service.UserService;
import com.crop.utils.CropJSONResult;
import com.crop.utils.PagedResult;
import com.crop.utils.RedisUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.crop.utils.RedisUtils.VIEW_COUNT;

/**
 * @Auther: fyp
 * @Date: 2022/4/5
 * @Description:
 * @Package: com.crop.admin.controller
 * @Version: 1.0
 */
@Slf4j
@RestController
@RequestMapping(value = "/admin/article")
@Api(value = "文章相关业务的接口", tags = {"文章相关业务的controller"})
public class AdminArticleController extends BasicController {

    @Autowired
    private UserService userService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ClassficationService classficationService;

    private static ScheduledExecutorService executor;


    @PostMapping(value = "/removeClassfication")
    @ApiOperation(value = "删除文章分类 - 注意: 文章分类为所有用户公共的分类，方便查询，私有分类情使用标签", notes = "删除文章分类的接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "classficationId", value = "文章分类id", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")

    })
    public CropJSONResult removeClassfication(String classficationId, String userId) {

        if (StringUtils.isBlank(userId)) {
            return CropJSONResult.errorMsg("用户id不能为空");
        }

        User user = userService.queryUser(userId);
        // 用户不存在 或 无权 删除
        if (user == null || user.getPermission() != 3) {
            return CropJSONResult.errorMsg("用户不存在或你无权执行该操作");
        }

        Article article = new Article();
        article.setClassId(classficationId);
        PagedResult pagedResult = articleService.queryArticleSelective(article, 1, 1);

        if (pagedResult.getRecords() != 0) {
            return CropJSONResult.errorMsg("删除失败！存在文章绑定了分类id: " + classficationId);
        }

        boolean deleteClassficationIsTrue = classficationService.deleteClassfication(classficationId);

        return deleteClassficationIsTrue ? CropJSONResult.ok() : CropJSONResult.errorMsg("分类id不存在或内部错误");
    }

    @PostMapping(value = "/updateClassfication")
    @ApiOperation(value = "更新文章分类", notes = "更新文章分类的接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "classfication", value = "文章分类", required = true, dataType = "Classfication", paramType = "body"),
            @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")

    })
    public CropJSONResult updateClassfication(@RequestBody Classfication classfication, String userId) {

        if (StringUtils.isBlank(userId)) {
            return CropJSONResult.errorMsg("用户id不能为空");
        }

        User user = userService.queryUser(userId);
        // 用户不存在 或 无权 删除
        if (user == null || user.getPermission() != 3) {
            return CropJSONResult.errorMsg("用户不存在或你无权执行该操作");
        }

        boolean updateClassficationIsTrue = classficationService.updateClassfication(classfication);

        return updateClassficationIsTrue ? CropJSONResult.ok() : CropJSONResult.errorMsg("分类id不存在或内部错误导致更新失败");
    }

    @PostMapping(value = "/saveClassfication")
    @ApiOperation(value = "新建文章分类", notes = "新建文章分类的接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "classficationName", value = "分类名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")
    })

    public CropJSONResult saveClassFication(String classficationName, String userId) {

        if (StringUtils.isBlank(classficationName)) {
            return CropJSONResult.errorMsg("分类名不能为空");
        }

        User user = userService.queryUser(userId);
        // 用户不存在 或 无权 删除
        if (user == null || user.getPermission() != 3) {
            return CropJSONResult.errorMsg("用户不存在或你无权执行该操作");
        }

        Classfication classfication = new Classfication();
        classfication.setName(classficationName);

        Classfication classficationIsExist = classficationService.queryClassfication(classfication);
        if (classficationIsExist != null) {
            return CropJSONResult.errorMsg("分类名已存在");
        }
        boolean saveIsTrue = classficationService.saveClassfication(classfication);

        return saveIsTrue ? CropJSONResult.ok() : CropJSONResult.errorMsg("内部错误导致保存失败");
    }


    @PostMapping(value = "/startTimeTask")
    @ApiOperation(value = "开启任务 - 自动更新阅读量", notes = "开启任务的接口")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")
    public CropJSONResult startTimeTask(String userId) {

        if (StringUtils.isBlank(userId)) {
            return CropJSONResult.errorMsg("用户Id不能为空");
        }

        User user = userService.queryUser(userId);
        if (user == null || user.getPermission() != 3) {
            return CropJSONResult.errorMsg("用户Id不存在或无权限");
        }

        if (executor != null && !executor.isShutdown()) {
            return CropJSONResult.errorMsg("任务已启动，无需重启");
        }
        executor = Executors.newScheduledThreadPool(2);
        log.info("正在开启任务线程池...");
        long initialDelay = 1 * 1000;
        long fiveMinute = 5 * 60 * 1000;
        executor.scheduleAtFixedRate(() -> {
            /**
             * 定时任务 - 更新 阅读量
             */
            // 获取 所有用户 对Id的 阅读量
            String viewCount = RedisUtils.getViewCount();
            List<String> keys = redis.getKeysByPrefix(viewCount);
            /**
             * key格式: "crop:viewCount:2204057942HA6Z7C"
             * 获取 articleId : 2204057942HA6Z7C
             */
            List<String> articleIdKeys = new ArrayList<>();
            Map<String, String> articleMap = new HashMap<>();



            for (String k : keys) {
                // 匹配 最后一个 : 到结束
                String tempArticleId = k.substring(k.lastIndexOf(":") + 1);
                articleIdKeys.add(tempArticleId);
            }

            List<String> articleIdCounts = redis.multiGet(keys);

            for (int i = 0; i < articleIdKeys.size(); i++) {
                articleMap.put(articleIdKeys.get(i), articleIdCounts.get(i));
            }

            articleService.multiUpdateArticleReadCounts(articleIdKeys, articleMap);
            log.info("完成一次周期任务 - 任务正常");

        }, initialDelay, fiveMinute, TimeUnit.MILLISECONDS);

        return CropJSONResult.ok("任务启动成功");

    }

    @PostMapping(value = "/stopTimeTask")
    @ApiOperation(value = "关闭任务", notes = "关闭任务的接口")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")
    public CropJSONResult stopTimeTask(String userId) {

        if (StringUtils.isBlank(userId)) {
            return CropJSONResult.errorMsg("用户Id不能为空");
        }

        User user = userService.queryUser(userId);
        if (user == null || user.getPermission() != 3) {
            return CropJSONResult.errorMsg("用户Id不存在或无权限");
        }

        if (executor == null || executor.isTerminated()) {
            return CropJSONResult.errorMsg("任务未启动");
        }
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("任务线程池关闭失败: ",e);
            executor.shutdownNow();
            //e.printStackTrace();
        }
        log.info("任务线程池已成功关闭");
        return CropJSONResult.ok("任务关闭成功");
    }



}
