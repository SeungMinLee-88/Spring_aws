package com.book_conference.dataroom.controller;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;
import com.book_conference.dataroom.dao.DataRoomIDao;
import com.book_conference.dataroom.dto.DownloadDto;
import com.book_conference.dataroom.dto.VideoDto;
import com.oreilly.servlet.MultipartRequest;
import com.oreilly.servlet.multipart.DefaultFileRenamePolicy;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.acmpca.model.Permission;
import com.amazonaws.services.guardduty.model.AccessControlList;
import com.amazonaws.services.macie2.model.BucketPublicAccess;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Builder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Grantee;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PublicAccessBlockConfiguration;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SetBucketAclRequest;
import com.amazonaws.services.s3.model.SetBucketPolicyRequest;
import com.amazonaws.services.s3.model.SetObjectAclRequest;
import com.amazonaws.services.s3.model.SetPublicAccessBlockRequest;


@Controller
public class DataRoomController {
	@Autowired
	private SqlSession slqSession;
	// 자료실 리스트
	@RequestMapping("/DownloadList")
	
	
	public String DownloadList(HttpServletRequest request,Model model) {
	String searchtype="";
	String searchkeyword="";
	if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
		searchtype=request.getParameter("searchtype");
	}
	if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
		searchkeyword=request.getParameter("searchkeyword");
	}
	
	List<DownloadDto> topitemList=null;
	List<DownloadDto> itemList=null;

	int count=1;
	int page=1;			
	int pageSize=7;		
	if(request.getParameter("page")!=null){
		page=Integer.parseInt(request.getParameter("page"));
	}
	
	DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);

	itemList = Idao.SelectDownloadList(searchtype, searchkeyword,page);
	topitemList = Idao.SelectDownloadTopList();
	count=Idao.SelectDownloadCount(searchtype, searchkeyword,page);
	
	int pageCount=count/pageSize+(count % pageSize==0?0:1);
	int startPage=(int)((page-1)/10)*10+1;
	int endPage=startPage+10-1;
	if (endPage>pageCount) endPage=pageCount;
	request.setAttribute("itemList", itemList);
	request.setAttribute("topitemList", topitemList);
	request.setAttribute("count", page);
	request.setAttribute("pageCount", pageCount);
	request.setAttribute("startPage", startPage);
	request.setAttribute("endPage", endPage);
	request.setAttribute("searchtype", searchtype);
	request.setAttribute("searchkeyword", searchkeyword);

	return "/data_room/download_list.datatiles";
	}
	private static final String BUCKET_NAME = "bookconference";
	private static final String ACCESS_KEY = "AKIAYMUF32C3S3K5DUQF";//AKIAS7ZSK5WMFVROAG4L
	private static final String SECRET_KEY = "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";//AlbUtiGyNiRsIpELEPKCQnhdEep4bNW0cnSM6ig7
	private AmazonS3 s3;
	// 자료실 글쓰기 페이지 요청
	@RequestMapping("/DownloadWrite")
	public String DownloadWrite(HttpServletRequest request,Model model){

		return "/data_room/download_write.datatiles";
	}
	// 자료실 글쓰기 처리
	@RequestMapping("/DownloadWriteProcess")
	public String DownloadWriteProcess(@RequestParam("file") MultipartFile file, HttpServletRequest request,Model model) throws IOException{
		String fileexits = file.getOriginalFilename();
		String orginalfilename = "";
		String filetransfername = "";
		String imageUrl = "";
		AWSCredentials awsCredentials = new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY);
		s3 = AmazonS3Client.builder()
				.withRegion(Regions.AP_NORTHEAST_2) /* 서울 */
				.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();
		String bucketName ="bookconference";
		String b;
		String policyText="{\"Version\": \"2012-10-17\",\"Id\": \"Policy1616561535270\",\"Statement\": [{\"Effect\": \"Allow\",\"Principal\": {\"AWS\": \"arn:aws:iam::070901764648:user/full\"},\"Action\": \"s3:ListBucket\",\"Resource\": \"arn:aws:s3:::bookconference\"}]}";

		if (s3.doesBucketExistV2(bucketName)) {
		    System.out.format("Bucket %s already exists.\n", bucketName);

		} else {
		    try {
		        s3.createBucket(bucketName);
			    s3.setPublicAccessBlock(new SetPublicAccessBlockRequest()
			    		.withBucketName(bucketName)
			    		.withPublicAccessBlockConfiguration(new PublicAccessBlockConfiguration()
			    				.withBlockPublicAcls(false)
			    				.withIgnorePublicAcls(false)
			    				.withBlockPublicPolicy(false)
			    				.withRestrictPublicBuckets(false)));
				s3.setBucketPolicy(bucketName, policyText);
		    } catch (AmazonS3Exception e) {
		        System.err.println(e.getErrorMessage());
		    }
		}
		com.amazonaws.services.s3.model.AccessControlList acl = new com.amazonaws.services.s3.model.AccessControlList();
		CanonicalGrantee cangrantee = new CanonicalGrantee("ea1c0d727d616130806a0f0cfb60d83d8440aaa9d8a9068a5d6bb9d4f128656a");
		Grant newgrantee = new Grant(cangrantee, com.amazonaws.services.s3.model.Permission.FullControl);
		Owner owner = new Owner();
		owner.setId("ea1c0d727d616130806a0f0cfb60d83d8440aaa9d8a9068a5d6bb9d4f128656a");
		acl.setOwner(owner);
		acl.grantAllPermissions(newgrantee);
		
		SetBucketAclRequest setBucketAclRequest = new SetBucketAclRequest(bucketName, CannedAccessControlList.PublicRead);
		s3.setBucketAcl(bucketName, acl);

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
		String currentDate = dateFormat.format(new Date());
		
		orginalfilename = file.getOriginalFilename();
		filetransfername = currentDate + file.getOriginalFilename();
		File localFile = new File("C:/workspace_gain/book_conference/src/main/webapp/WEB-INF/book_conference/downloadfolder/" + filetransfername);
		file.transferTo(localFile);
		PutObjectRequest obj = new PutObjectRequest(BUCKET_NAME, localFile.getName(), localFile);
		s3.putObject(obj);
		String objkey = obj.getKey();
		s3.setObjectAcl("bookconference", objkey, CannedAccessControlList.PublicReadWrite);
		imageUrl = "https://bookconference.s3.ap-northeast-2.amazonaws.com/"+localFile.getName();
//}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		HttpSession session=request.getSession(); 
		String memberid = (String)session.getAttribute("id");
		String DOWNLOAD_TOP="n";
		Idao.DownloadWriteProcess(
			request.getParameter("DOWNLOAD_TITLE"),
			request.getParameter("DOWNLOAD_CONTENT"),
			memberid,
			orginalfilename,
			filetransfername,
			imageUrl,
			DOWNLOAD_TOP);
			
			String returnmsg ="등록 되었습니다.";
			request.setAttribute("msg", returnmsg); 
			request.setAttribute("url", "./DownloadList");
			return "/alertredirect/redirect";
	}
	// 자료실 글 상세보기
	@RequestMapping("/DownloadView")
	public String DownloadView(HttpServletRequest request,Model model){
		String searchtype="";
		String searchkeyword="";
		String download_number = request.getParameter("download_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		DownloadDto Dto = Idao.SelectDownloadInfo(download_number);
		Idao.DownloadHitCountIncrese(download_number);
		request.setAttribute("Dto", Dto);
		request.setAttribute("realfilenamelist", Dto.getDOWNLOAD_FILENAME());
		request.setAttribute("savefilenamelist", Dto.getDOWNLOAD_FILE());
		request.setAttribute("count", page);
		request.setAttribute("searchtype", searchtype);
		request.setAttribute("searchkeyword", searchkeyword);

		return "/data_room/download_view.datatiles";
	}
	// 자료실 글 수정 페이지 요청
	@RequestMapping("/DownloadModify")
	public String DownloadModify(HttpServletRequest request,Model model){
		String searchtype="";
		String searchkeyword="";
		String download_number = request.getParameter("download_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		DownloadDto Dto = Idao.SelectDownloadInfo(download_number);
		Idao.DownloadHitCountIncrese(download_number);
		request.setAttribute("Dto", Dto);
		request.setAttribute("realfilenamelist", Dto.getDOWNLOAD_FILENAME());
		request.setAttribute("savefilenamelist", Dto.getDOWNLOAD_FILE());
		request.setAttribute("count", page);
		request.setAttribute("searchtype", searchtype);
		request.setAttribute("searchkeyword", searchkeyword);

		return "/data_room/download_modify.datatiles";
	}
	// 자료실 글 수정
	@RequestMapping("/DownloadModifyProcess")
	public String DownloadModifyProcess(HttpServletRequest request,Model model) throws IOException{
		
		String realPath = "";
		String savePath = "/WEB-INF/book_conference/downloadfolder/";
		int maxSize = 5 * 1024 * 1024;
		realPath = request.getServletContext().getRealPath(savePath);
		List<String> savefiles=new ArrayList<String>();
		List<String> realfiles=new ArrayList<String>();
		
		MultipartRequest multi = null;
		multi = new MultipartRequest(request, realPath, maxSize, "UTF-8",
					new DefaultFileRenamePolicy());
		
		String searchtype="";
		String searchkeyword="";
		String download_number = multi.getParameter("download_number");
		int page=1;	
		if(multi.getParameter("searchtype")!=null && multi.getParameter("searchtype")!=""){
			searchtype=multi.getParameter("searchtype");
		}
		if(multi.getParameter("searchkeyword")!=null && multi.getParameter("searchkeyword")!=""){
			searchkeyword=multi.getParameter("searchkeyword");
		}
		if(multi.getParameter("page")!=null){
			page=Integer.parseInt(multi.getParameter("page"));
		}
		String DelFile= 
				"C:/workspace_new/.metadata/.plugins/org.eclipse.wst.server.core/tmp1/wtpwebapps/book_conference/WEB-INF/book_conference/downloadfolder/";
		for(int delnum=1;delnum<=3;delnum++){
			if(multi.getParameter("checkbox"+delnum)!=null){
				if(multi.getParameter("checkbox"+delnum).equals("y")){
					File file = new File(DelFile+multi.getParameter("savename"+delnum));
					if(file.exists() == true){
						file.delete();
					}
				}
			}
		}
		Enumeration<?> files=multi.getFileNames();
		int savenum=1;
		while(files.hasMoreElements()){
				String name=(String)files.nextElement();
				if(files.hasMoreElements()){
					if(multi.getParameter("checkbox"+savenum)!=null){
						savefiles.add(multi.getFilesystemName(name)+",");
						realfiles.add(multi.getOriginalFileName(name)+",");
					}else if(multi.getParameter("checkbox"+savenum)==null){
						if(multi.getParameter("savename"+savenum) != null){
							savefiles.add(multi.getParameter("savename"+savenum)+",");
							realfiles.add(multi.getParameter("realname"+savenum)+",");
						}else if(multi.getParameter("savename"+savenum) == null){
							savefiles.add(multi.getFilesystemName(name)+",");
							realfiles.add(multi.getOriginalFileName(name)+",");
						}
					}
				}else{
					if(multi.getParameter("checkbox"+savenum)!=null){
						savefiles.add(multi.getFilesystemName(name));
						realfiles.add(multi.getOriginalFileName(name));
					}else if(multi.getParameter("checkbox"+savenum)==null){
						if(multi.getParameter("savename"+savenum) != null){
							savefiles.add(multi.getParameter("savename"+savenum));
							realfiles.add(multi.getParameter("realname"+savenum));
						}else if(multi.getParameter("savename"+savenum) == null){
							savefiles.add(multi.getFilesystemName(name));
							realfiles.add(multi.getOriginalFileName(name));
						}
					}
				}
				savenum++;
			}	
			StringBuffer savefilename=new StringBuffer();
			for(int i=0;i<savefiles.size();i++){
				savefilename.append(savefiles.get(i));	
			}
			
			StringBuffer realfilename=new StringBuffer();
			for(int i=0;i<realfiles.size();i++){
				realfilename.append(realfiles.get(i));	
			}

		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		HttpSession session=request.getSession(); 
		String memberid = (String)session.getAttribute("id");
		String DOWNLOAD_TOP=null;
		if(multi.getParameter("DOWNLOAD_TOP")!=null && multi.getParameter("DOWNLOAD_TOP")!=""){
			DOWNLOAD_TOP="y";
		}else if(multi.getParameter("DOWNLOAD_TOP")==null || multi.getParameter("DOWNLOAD_TOP")==""){
			DOWNLOAD_TOP="n";
		}
		Idao.DownloadModifyProcess(
			multi.getParameter("DOWNLOAD_TITLE"),
			multi.getParameter("DOWNLOAD_CONTENT"),
			memberid,
			realfilename.toString(),
			savefilename.toString(),
			DOWNLOAD_TOP,
			download_number);
			String returnmsg ="수정 되었습니다.";
			request.setAttribute("msg", returnmsg); 
			request.setAttribute("url", "./DownloadView?download_number="+download_number+"&page="+page+"&searchtype="+searchtype+"&searchkeyword="+searchkeyword);
			return "/alertredirect/redirect";
	}
	
	// 자료실 글 삭제
	@RequestMapping("/DownloadDelete")
	public String DownloadDelete(HttpServletRequest request,Model model) throws IOException{
		String searchtype="";
		String searchkeyword="";
		AWSCredentials awsCredentials = new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY);
		
		String download_number = request.getParameter("download_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		DownloadDto Dto = Idao.SelectDownloadInfo(download_number);
		/*List<String> savefilenamelist = Arrays.asList(Dto.getDOWNLOAD_FILE().split(","));*/
		String savefilename = Dto.getDOWNLOAD_FILE();
		String DelFile= 
"C:/workspace_gain/book_conference/src/main/webapp/WEB-INF/book_conference/downloadfolder/";
		/*for(int delnum=0;delnum<savefilenamelist.size();delnum++){*/
					File file = new File(DelFile+savefilename);
					if(file.exists() == true){
						file.delete();
					}
		/*}*/
		
		s3 = AmazonS3Client.builder()
				.withRegion(Regions.AP_NORTHEAST_2) /* 서울 */
				.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
		String currentDate = dateFormat.format(new Date());
		
		try {
		    s3.deleteObject(BUCKET_NAME, savefilename);
		} catch (AmazonServiceException e) {
		    System.err.println(e.getErrorMessage());
		    System.exit(1);
		}
		
		Idao.DownloadDelete(download_number);

		String returnmsg ="삭제 되었습니다.";
		request.setAttribute("msg", returnmsg); 
		request.setAttribute("url", "./DownloadList?page="+page+"&searchtype="+searchtype+"&searchkeyword="+searchkeyword);
		return "/alertredirect/redirect";
	}
	// 파일 다운로드 뷰 페이지 요청
	@RequestMapping(value = "FileDownload")

	public ModelAndView FileDownload(HttpServletRequest request, HttpServletResponse response) throws Exception {
	String filename = request.getParameter("filename");
	String downname = request.getParameter("downname");
	String fileFullPath = 
"C:/workspace_new/.metadata/.plugins/org.eclipse.wst.server.core/tmp1/wtpwebapps/book_conference/WEB-INF/book_conference/downloadfolder/";
	fileFullPath += filename;
		
    File downloadFile = new File(fileFullPath);
    File downloadFilename = new File(downname);
    if(!downloadFile.canRead()){
        throw new Exception("File can't read(파일을 찾을 수 없습니다)");
    }
	   ModelAndView mav = new ModelAndView();

	    mav.setViewName("fileDownloadView");

	    mav.addObject("downloadFile", downloadFile); // 실제 저장된 파일

	    mav.addObject("downloadFilename", downloadFilename); //db에 저장해 놓은 원래 파일이름

	    
	    return mav;
	}
	
/* VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO VIDEO */
	// 영상 자료실 리스트 요청
	@RequestMapping("/VideoList")
	public String VideoList(HttpServletRequest request,Model model) {
	String searchtype="";
	String searchkeyword="";
	if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
		searchtype=request.getParameter("searchtype");
	}
	if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
		searchkeyword=request.getParameter("searchkeyword");
	}
	List<VideoDto> topitemList=null;
	List<VideoDto> itemList=null;

	int count=1;
	int page=1;			
	int pageSize=7;		
	if(request.getParameter("page")!=null){
		page=Integer.parseInt(request.getParameter("page"));
	}
	DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);

	itemList = Idao.SelectVideoList(searchtype, searchkeyword,page);
	topitemList = Idao.SelectVideoTopList();
	count=Idao.SelectVideoCount(searchtype, searchkeyword,page);
	
	int pageCount=count/pageSize+(count % pageSize==0?0:1);
	int startPage=(int)((page-1)/10)*10+1;
	int endPage=startPage+10-1;
	if (endPage>pageCount) endPage=pageCount;
	request.setAttribute("itemList", itemList);
	request.setAttribute("topitemList", topitemList);
	request.setAttribute("count", page);
	request.setAttribute("pageCount", pageCount);
	request.setAttribute("startPage", startPage);
	request.setAttribute("endPage", endPage);
	request.setAttribute("searchtype", searchtype);
	request.setAttribute("searchkeyword", searchkeyword);

	return "/data_room/video_list.datatiles";
	}
	
	// 영상 자료실 글쓰기 폼 요청
	@RequestMapping("/VideoWrite")
	public String VideoWrite(HttpServletRequest request,Model model){

		return "/data_room/video_write.datatiles";
	}
	//영상 자료실 글쓰기 처리
	@RequestMapping("/VideoWriteProcess")
	public String VideoWriteProcess(HttpServletRequest request,Model model) throws IOException{
		String realPath = "";
		String savePath = "/WEB-INF/book_conference/videofolder/";

		int maxSize = 5 * 1024 * 1024;
		realPath = request.getServletContext().getRealPath(savePath);
		
		List<String> savefiles=new ArrayList<String>();
		List<String> realfiles=new ArrayList<String>();
		
		MultipartRequest multi = null;
		multi = new MultipartRequest(request, realPath, maxSize, "UTF-8",
				new DefaultFileRenamePolicy());
		String realfilenamestr = "";
		String upfilenamestr = "";
		String videolinkstr="";
		if(multi.getParameter("VIDEO_LINK")!=null && multi.getParameter("VIDEO_LINK")!=""){
			videolinkstr=multi.getParameter("VIDEO_LINK");
		}
if(multi.getParameter("VIDEO_TYPE").equals("file")){
			
		Enumeration<?> files=multi.getFileNames();
		while(files.hasMoreElements()){
				String name=(String)files.nextElement();
				if(files.hasMoreElements()){
					savefiles.add(multi.getFilesystemName(name)+",");
					realfiles.add(multi.getOriginalFileName(name)+",");
				}else{
					savefiles.add(multi.getFilesystemName(name));
					realfiles.add(multi.getOriginalFileName(name));
				}
			}	
			StringBuffer upfilename=new StringBuffer();
			for(int i=0;i<savefiles.size();i++){
				upfilename.append(savefiles.get(i));	
			}
			
			StringBuffer realfilename=new StringBuffer();
			for(int i=0;i<realfiles.size();i++){
				realfilename.append(realfiles.get(i));	
			}
			realfilenamestr =realfilename.toString();
			upfilenamestr = upfilename.toString();
	}
	DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
	HttpSession session=request.getSession(); 
	String memberid = (String)session.getAttribute("id");
	String VIDEO_TOP=null;
	if(multi.getParameter("VIDEO_TOP")!=null && multi.getParameter("VIDEO_TOP")!=""){
		VIDEO_TOP="y";
	}else if(multi.getParameter("VIDEO_TOP")==null || multi.getParameter("VIDEO_TOP")==""){
		VIDEO_TOP="n";
	}
	
	Idao.VideoWriteProcess(
		multi.getParameter("VIDEO_TITLE"),
		multi.getParameter("VIDEO_CONTENT"),
		memberid,
		realfilenamestr,
		upfilenamestr,
		videolinkstr,
		multi.getParameter("VIDEO_TYPE"),
		VIDEO_TOP
		);
		
		String returnmsg ="등록 되었습니다.";
		request.setAttribute("msg", returnmsg); 
		request.setAttribute("url", "./VideoList");
		return "/alertredirect/redirect";
	}
	// 영상 자료실 글 상세 보기
	@RequestMapping("/VideoView")
	public String VideoView(HttpServletRequest request,Model model){
		String searchtype="";
		String searchkeyword="";
		String download_number = request.getParameter("video_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		VideoDto Dto = Idao.SelectVideoInfo(download_number);
		Idao.DownloadHitCountIncrese(download_number);
		String fileFullPath = "http://localhost:8080/book_conference/videofolder/";
		fileFullPath += Dto.getVIDEO_FILENAME();
		request.setAttribute("Dto", Dto);
		request.setAttribute("realfilename", Dto.getVIDEO_FILENAME());
		request.setAttribute("savefilename", Dto.getVIDEO_FILE());
		request.setAttribute("fileFullPath", fileFullPath);
		request.setAttribute("count", page);
		request.setAttribute("searchtype", searchtype);
		request.setAttribute("searchkeyword", searchkeyword);

		return "/data_room/video_view.datatiles";
	}
	
	// 영상 자료실 글 수정 폼 요청
	@RequestMapping("/VideoModify")
	public String VideoModify(HttpServletRequest request,Model model){
		String searchtype="";
		String searchkeyword="";
		String video_number = request.getParameter("video_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		VideoDto Dto = Idao.SelectVideoInfo(video_number);
		Idao.DownloadHitCountIncrese(video_number);
		
		request.setAttribute("Dto", Dto);
		request.setAttribute("realfilenamelist", Dto.getVIDEO_FILENAME());
		request.setAttribute("savefilenamelist", Dto.getVIDEO_FILE());
		request.setAttribute("videolink", Dto.getVIDEO_LINK());
		request.setAttribute("videotype", Dto.getVIDEO_TYPE());
		
		request.setAttribute("count", page);
		request.setAttribute("searchtype", searchtype);
		request.setAttribute("searchkeyword", searchkeyword);

		return "/data_room/video_modify.datatiles";
	}
	
	// 영상 자료실 글 수정 처리
	@RequestMapping("/VideoModifyProcess")
	public String VideoModifyProcess(HttpServletRequest request,Model model) throws IOException{
		
		String realPath = "";
		String savePath = "/WEB-INF/book_conference/videofolder/";

		int maxSize = 5 * 1024 * 1024;
		realPath = request.getServletContext().getRealPath(savePath);
		
		List<String> savefiles=new ArrayList<String>();
		List<String> realfiles=new ArrayList<String>();
		
		MultipartRequest multi = null;
		multi = new MultipartRequest(request, realPath, maxSize, "UTF-8",
					new DefaultFileRenamePolicy());
		
		String searchtype="";
		String searchkeyword="";
		String video_number = multi.getParameter("video_number");
		
		String videolinkstr="";
		if(multi.getParameter("VIDEO_LINK")!=null && multi.getParameter("VIDEO_LINK")!=""){
			videolinkstr=multi.getParameter("VIDEO_LINK");
		}
		int page=1;	
		if(multi.getParameter("searchtype")!=null && multi.getParameter("searchtype")!=""){
			searchtype=multi.getParameter("searchtype");
		}
		if(multi.getParameter("searchkeyword")!=null && multi.getParameter("searchkeyword")!=""){
			searchkeyword=multi.getParameter("searchkeyword");
		}
		if(multi.getParameter("page")!=null){
			page=Integer.parseInt(multi.getParameter("page"));
		}
		String DelFile= 
				"C:/workspace_new/.metadata/.plugins/org.eclipse.wst.server.core/tmp1/wtpwebapps/book_conference/WEB-INF/book_conference/videofolder/";
		for(int delnum=1;delnum<=3;delnum++){
			if(multi.getParameter("checkbox"+delnum)!=null || multi.getParameter("VIDEO_TYPE").equals("link")){
					File file = new File(DelFile+multi.getParameter("savename"+delnum));
					if(file.exists() == true){
						file.delete();
					}
			}
		}
		Enumeration<?> files=multi.getFileNames();
		int savenum=1;
		while(files.hasMoreElements()){
				String name=(String)files.nextElement();
				if(files.hasMoreElements()){
					if(multi.getParameter("checkbox"+savenum)!=null){
						savefiles.add(multi.getFilesystemName(name)+",");
						realfiles.add(multi.getOriginalFileName(name)+",");
					}else if(multi.getParameter("checkbox"+savenum)==null){
						if(multi.getParameter("savename"+savenum) != null){
							savefiles.add(multi.getParameter("savename"+savenum)+",");
							realfiles.add(multi.getParameter("realname"+savenum)+",");
						}else if(multi.getParameter("savename"+savenum) == null){
							savefiles.add(multi.getFilesystemName(name)+",");
							realfiles.add(multi.getOriginalFileName(name)+",");
						}
					}
				}else{
					
					if(multi.getParameter("checkbox"+savenum)!=null){
						savefiles.add(multi.getFilesystemName(name));
						realfiles.add(multi.getOriginalFileName(name));
					}else if(multi.getParameter("checkbox"+savenum)==null){
						if(multi.getParameter("savename"+savenum) != null && !multi.getParameter("savename1").equals("")&& !multi.getParameter("savename1").equals("null")){

							savefiles.add(multi.getParameter("savename"+savenum));
							realfiles.add(multi.getParameter("realname"+savenum));
						}else if(multi.getParameter("savename"+savenum) == null || multi.getParameter("savename1").equals("") || multi.getParameter("savename1").equals("null")){
							savefiles.add(multi.getFilesystemName(name));
							realfiles.add(multi.getOriginalFileName(name));
						}
					}
				}
				savenum++;
			}	
			StringBuffer savefilename=new StringBuffer();
			for(int i=0;i<savefiles.size();i++){
				savefilename.append(savefiles.get(i));	
			}
			
			StringBuffer realfilename=new StringBuffer();
			for(int i=0;i<realfiles.size();i++){
				realfilename.append(realfiles.get(i));	
			}
		
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		HttpSession session=request.getSession(); 
		String memberid = (String)session.getAttribute("id");
		String VIDEO_TOP=null;
		if(multi.getParameter("VIDEO_TOP")!=null && multi.getParameter("VIDEO_TOP")!=""){
			VIDEO_TOP="y";
		}else if(multi.getParameter("VIDEO_TOP")==null || multi.getParameter("VIDEO_TOP")==""){
			VIDEO_TOP="n";
		}
		Idao.VideoModifyProcess(
			multi.getParameter("VIDEO_TITLE"),
			multi.getParameter("VIDEO_CONTENT"),
			memberid,
			realfilename.toString(),
			savefilename.toString(),
			videolinkstr,
			multi.getParameter("VIDEO_TYPE"),
			VIDEO_TOP,
			video_number);
		
			String returnmsg ="수정 되었습니다.";
			request.setAttribute("msg", returnmsg); 
			request.setAttribute("url", "./VideoView?video_number="+video_number+"&page="+page+"&searchtype="+searchtype+"&searchkeyword="+searchkeyword);
			return "/alertredirect/redirect";
	}
	// 영상 자료실 글 삭제
	@RequestMapping("/VideoDelete")
	public String VideoDelete(HttpServletRequest request,Model model) throws IOException{
		String searchtype="";
		String searchkeyword="";

		String video_number = request.getParameter("video_number");
		int page=1;	
		if(request.getParameter("searchtype")!=null && request.getParameter("searchtype")!=""){
			searchtype=request.getParameter("searchtype");
		}
		if(request.getParameter("searchkeyword")!=null && request.getParameter("searchkeyword")!=""){
			searchkeyword=request.getParameter("searchkeyword");
		}
		if(request.getParameter("page")!=null){
			page=Integer.parseInt(request.getParameter("page"));
		}
		
		DataRoomIDao Idao = slqSession.getMapper(DataRoomIDao.class);
		VideoDto Dto = Idao.SelectVideoInfo(video_number);
if(Dto.getVIDEO_FILE()!=null && !Dto.getVIDEO_FILE().equals("null") &&!Dto.getVIDEO_FILE().equals("")){
		List<String> savefilenamelist = Arrays.asList(Dto.getVIDEO_FILE().split(","));
		String DelFile= 
"C:/workspace_new/.metadata/.plugins/org.eclipse.wst.server.core/tmp1/wtpwebapps/book_conference/WEB-INF/book_conference/videofolder/";

		for(int delnum=0;delnum<savefilenamelist.size();delnum++){
					File file = new File(DelFile+savefilenamelist.get(delnum));
					if(file.exists() == true){
						file.delete();
					}
		}
}
		Idao.VideoDelete(video_number);
		String returnmsg ="삭제 되었습니다.";
		request.setAttribute("msg", returnmsg); 
		request.setAttribute("url", "./VideoList?page="+page+"&searchtype="+searchtype+"&searchkeyword="+searchkeyword);
		return "/alertredirect/redirect";
	}
}
